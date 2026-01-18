package discovery

import (
	"context"
	"fmt"
	"os"

	"github.com/grandcat/zeroconf"
)

const (
	Service  = "_landrop._tcp"
	Domain   = "local."
	TextData = "version=1.0"
)

// Announce broadcasts the service presence on the network.
// It returns a shutdown function that should be called when the service is stopped.
func Announce(ctx context.Context, port int) (func(), error) {
	hostname, err := os.Hostname()
	if err != nil {
		hostname = "unknown-device"
	}

	// Instance name usually needs to be unique. Zeroconf might handle conflicts or we append random string.
	// For simplicity, we use hostname. In a real app, might want a UUID or similar.
	instanceName := fmt.Sprintf("%s-landrop", hostname)

	server, err := zeroconf.Register(
		instanceName,
		Service,
		Domain,
		port,
		[]string{TextData},
		nil,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to register service: %w", err)
	}

	shutdown := func() {
		server.Shutdown()
	}

	return shutdown, nil
}

// Browse scans for available Landrop peers.
// It sends found entries to the provided channel.
func Browse(ctx context.Context, entries chan<- *zeroconf.ServiceEntry) error {
	resolver, err := zeroconf.NewResolver(nil)
	if err != nil {
		return fmt.Errorf("failed to create resolver: %w", err)
	}

	if err := resolver.Browse(ctx, Service, Domain, entries); err != nil {
		return fmt.Errorf("failed to browse: %w", err)
	}

	return nil
}
