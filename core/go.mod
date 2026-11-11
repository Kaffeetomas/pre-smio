module miotest/androidcore

go 1.27.3

require (
	github.com/orgmio/mio v0.0.0
	github.com/xjasonlyu/tun2socks/v2 v2.0.0
	golang.org/x/net v0.62.0
	golang.org/x/sys v0.50.0
)

require (
	github.com/andybalholm/brotli v1.3.0 // indirect
	github.com/apernet/quic-go v0.65.1-0.20261109005002-2895f039137a // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/klauspost/compress v1.20.4 // indirect
	github.com/pelletier/go-toml/v2 v2.4.3 // indirect
	github.com/refraction-networking/utls v1.8.2 // indirect
	github.com/things-go/go-socks5 v0.1.4 // indirect
	golang.org/x/crypto v0.58.0 // indirect
	golang.org/x/exp v0.0.0-20261011100332-faecfb24eb49 // indirect
	golang.org/x/time v0.16.0 // indirect
	gvisor.dev/gvisor v0.0.0-20261010064756-cd110d9dec04 // indirect
)

replace github.com/orgmio/mio => ../vendor/mio

replace github.com/xjasonlyu/tun2socks/v2 => ../vendor/tun2socks

replace github.com/apernet/quic-go => github.com/HyNetworks/quic-go v0.65.1-0.20261109005002-2895f039137a
