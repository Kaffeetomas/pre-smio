package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"fmt"
	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"io"
	"net"
	"os"
	"testing"
	"time"
)

type pipeTCP struct{ net.Conn }

func (c pipeTCP) ID() stack.TransportEndpointID {
	return stack.TransportEndpointID{LocalAddress: tcpip.AddrFrom4([4]byte{203, 0, 113, 7}), LocalPort: 443}
}

type pipeDialer struct {
	conn   net.Conn
	target chan string
}

func (d pipeDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	d.target <- address
	return d.conn, nil
}
func TestTCPBridgeBidirectional(t *testing.T) {
	app, inside := net.Pipe()
	remote, server := net.Pipe()
	defer app.Close()
	defer server.Close()
	deadline := time.Now().Add(3 * time.Second)
	app.SetDeadline(deadline)
	server.SetDeadline(deadline)
	targets := make(chan string, 1)
	(&bridge{dial: pipeDialer{conn: remote, target: targets}}).HandleTCP(pipeTCP{inside})
	result := make(chan error, 1)
	go func() {
		q := make([]byte, 7)
		if _, err := io.ReadFull(server, q); err != nil {
			result <- err
			return
		}
		if string(q) != "request" {
			result <- fmt.Errorf("request changed: %q", q)
			return
		}
		_, err := server.Write([]byte("reply"))
		result <- err
	}()
	if _, err := app.Write([]byte("request")); err != nil {
		t.Fatal(err)
	}
	reply := make([]byte, 5)
	if _, err := io.ReadFull(app, reply); err != nil {
		t.Fatal(err)
	}
	if string(reply) != "reply" {
		t.Fatalf("reply %q", reply)
	}
	if err := <-result; err != nil {
		t.Fatal(err)
	}
	if target := <-targets; target != "203.0.113.7:443" {
		t.Fatalf("target %s", target)
	}
}

type testDialer struct{ address string }

func (d testDialer) DialContext(ctx context.Context, network, address string) (net.Conn, error) {
	return (&net.Dialer{}).DialContext(ctx, network, d.address)
}
func TestDNSTCPFraming(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	query := []byte{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0}
	answer := []byte{0x12, 0x34, 0x81, 0x80, 0, 1, 0, 0, 0, 0, 0, 0}
	received := make(chan []byte, 1)
	go func() {
		c, e := listener.Accept()
		if e != nil {
			return
		}
		defer c.Close()
		header := make([]byte, 2)
		io.ReadFull(c, header)
		q := make([]byte, binary.BigEndian.Uint16(header))
		io.ReadFull(c, q)
		received <- q
		packet := make([]byte, 2+len(answer))
		binary.BigEndian.PutUint16(packet, uint16(len(answer)))
		copy(packet[2:], answer)
		c.Write(packet)
	}()
	got, err := dnsTCP(testDialer{listener.Addr().String()}, query)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, answer) {
		t.Fatalf("answer %x", got)
	}
	if !bytes.Equal(<-received, query) {
		t.Fatal("query changed")
	}
}
func TestDNSTCPRejectsTruncatedReply(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		c, e := listener.Accept()
		if e != nil {
			return
		}
		defer c.Close()
		b := make([]byte, 2)
		io.ReadFull(c, b)
		io.CopyN(io.Discard, c, int64(binary.BigEndian.Uint16(b)))
		c.Write([]byte{0, 10, 1})
	}()
	if _, err := dnsTCP(testDialer{listener.Addr().String()}, []byte{1, 2}); err == nil {
		t.Fatal("accepted truncated DNS response")
	}
}
func TestReceiveAndroidVPNDescriptor(t *testing.T) {
	name := fmt.Sprintf("mio-test-%d-%d", os.Getpid(), time.Now().UnixNano())
	result := make(chan int, 1)
	failure := make(chan error, 1)
	go func() {
		fd, err := receiveFD(name)
		if err != nil {
			failure <- err
		} else {
			result <- fd
		}
	}()
	var c *net.UnixConn
	var err error
	for n := 0; n < 100; n++ {
		c, err = net.DialUnix("unix", nil, &net.UnixAddr{Name: "@" + name, Net: "unix"})
		if err == nil {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	file, err := os.CreateTemp(t.TempDir(), "descriptor")
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	if _, err = file.Write([]byte("vpn-descriptor")); err != nil {
		t.Fatal(err)
	}
	file.Seek(0, 0)
	if _, _, err = c.WriteMsgUnix([]byte{1}, unix.UnixRights(int(file.Fd())), nil); err != nil {
		t.Fatal(err)
	}
	select {
	case fd := <-result:
		received := os.NewFile(uintptr(fd), "received")
		defer received.Close()
		data, err := io.ReadAll(received)
		if err != nil {
			t.Fatal(err)
		}
		if string(data) != "vpn-descriptor" {
			t.Fatalf("descriptor data %q", data)
		}
	case err := <-failure:
		t.Fatal(err)
	case <-time.After(5 * time.Second):
		t.Fatal("descriptor transfer timed out")
	}
}
