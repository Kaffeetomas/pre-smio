package main

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/orgmio/mio/client"
	"github.com/orgmio/mio/config"
	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/adapter"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"golang.org/x/net/proxy"
	"golang.org/x/sys/unix"
)

type settings struct {
	Server    string
	Port      int
	Dest      string
	DestPort  int
	Password  string
	Socket    string
	SocksPort int
}
type bridge struct{ dial proxy.ContextDialer }

func (b *bridge) HandleTCP(c adapter.TCPConn) {
	go func() {
		defer c.Close()
		id := c.ID()
		target := net.JoinHostPort(id.LocalAddress.String(), strconv.Itoa(int(id.LocalPort)))
		ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
		remote, err := b.dial.DialContext(ctx, "tcp", target)
		cancel()
		if err != nil {
			log.Printf("TCP connection: %v", err)
			return
		}
		defer remote.Close()
		done := make(chan struct{})
		go func() {
			io.Copy(remote, c)
			if x, ok := remote.(interface{ CloseWrite() error }); ok {
				x.CloseWrite()
			}
			close(done)
		}()
		io.Copy(c, remote)
		c.Close()
		remote.Close()
		<-done
	}()
}

// mio deliberately provides no application UDP tunnel. Translate DNS only
// to TCP through mio; reject other UDP so browsers can fall back to TCP.
func (b *bridge) HandleUDP(c adapter.UDPConn) {
	go func() {
		defer c.Close()
		if c.ID().LocalPort != 53 {
			return
		}
		buf := make([]byte, 65535)
		for {
			c.SetReadDeadline(time.Now().Add(30 * time.Second))
			n, from, err := c.ReadFrom(buf)
			if err != nil {
				return
			}
			answer, err := dnsTCP(b.dial, buf[:n])
			if err != nil {
				log.Printf("DNS: %v", err)
				continue
			}
			if _, err = c.WriteTo(answer, from); err != nil {
				return
			}
		}
	}()
}

func dnsTCP(d proxy.ContextDialer, query []byte) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	c, err := d.DialContext(ctx, "tcp", "1.1.1.1:53")
	if err != nil {
		return nil, err
	}
	defer c.Close()
	c.SetDeadline(time.Now().Add(15 * time.Second))
	packet := make([]byte, len(query)+2)
	binary.BigEndian.PutUint16(packet, uint16(len(query)))
	copy(packet[2:], query)
	if _, err = io.Copy(c, bytes.NewReader(packet)); err != nil {
		return nil, err
	}
	header := make([]byte, 2)
	if _, err = io.ReadFull(c, header); err != nil {
		return nil, err
	}
	answer := make([]byte, int(binary.BigEndian.Uint16(header)))
	_, err = io.ReadFull(c, answer)
	return answer, err
}

func receiveFD(name string) (int, error) {
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: "@" + name, Net: "unix"})
	if err != nil {
		return -1, err
	}
	defer l.Close()
	l.SetDeadline(time.Now().Add(20 * time.Second))
	c, err := l.AcceptUnix()
	if err != nil {
		return -1, err
	}
	defer c.Close()
	c.SetReadDeadline(time.Now().Add(5 * time.Second))
	data := make([]byte, 1)
	oob := make([]byte, unix.CmsgSpace(4))
	_, n, _, _, err := c.ReadMsgUnix(data, oob)
	if err != nil {
		return -1, err
	}
	msgs, err := unix.ParseSocketControlMessage(oob[:n])
	if err != nil {
		return -1, err
	}
	for _, m := range msgs {
		fds, e := unix.ParseUnixRights(&m)
		if e == nil && len(fds) > 0 {
			unix.CloseOnExec(fds[0])
			return fds[0], nil
		}
	}
	return -1, fmt.Errorf("VPN descriptor missing")
}

func run() error {
	if len(os.Args) != 2 {
		return fmt.Errorf("configuration path required")
	}
	raw, err := os.ReadFile(os.Args[1])
	if err != nil {
		return err
	}
	var s settings
	if err = json.Unmarshal(raw, &s); err != nil {
		return err
	}
	os.Remove(os.Args[1])
	if s.SocksPort == 0 {
		s.SocksPort = 21080
	}
	cfg := config.Config{Log: config.Log{Enable: true, Level: "info"}, Socks5: config.SOCKS5{Listen: "127.0.0.1", Port: s.SocksPort}, Peer: config.Peer{Server: s.Server, Port: s.Port, Dest: s.Dest, DestPort: s.DestPort, Password: s.Password}}
	if err = cfg.Validate(); err != nil {
		return err
	}
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	go func() {
		if err := client.NewSOCKS5(cfg.Socks5, cfg.Peer).Run(ctx); err != nil {
			log.Fatal(err)
		}
	}()
	fd, err := receiveFD(s.Socket)
	if err != nil {
		return err
	}
	d, err := proxy.SOCKS5("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(s.SocksPort)), nil, &net.Dialer{Timeout: 25 * time.Second})
	if err != nil {
		return err
	}
	dev, err := fdbased.Open(strconv.Itoa(fd), 1280, 0)
	if err != nil {
		return err
	}
	defer dev.Close()
	stack, err := core.CreateStack(&core.Config{LinkEndpoint: dev, TransportHandler: &bridge{dial: d.(proxy.ContextDialer)}})
	if err != nil {
		return err
	}
	defer stack.Close()
	fmt.Println("MIO_VPN_READY")
	go func() {
		transport := &http.Transport{DialContext: d.(proxy.ContextDialer).DialContext}
		defer transport.CloseIdleConnections()
		h := &http.Client{Transport: transport, Timeout: 35 * time.Second}
		response, e := h.Get("https://api.ipify.org")
		if e != nil {
			log.Printf("出口 IP 测试失败，可检查密码和防火墙：%v", e)
			return
		}
		defer response.Body.Close()
		body, e := io.ReadAll(io.LimitReader(response.Body, 100))
		if e == nil && response.StatusCode == 200 && net.ParseIP(strings.TrimSpace(string(body))) != nil {
			fmt.Println("MIO_EXIT_IP:", strings.TrimSpace(string(body)))
		} else {
			log.Print("出口 IP 测试未通过")
		}
	}()
	<-ctx.Done()
	return nil
}
func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}
