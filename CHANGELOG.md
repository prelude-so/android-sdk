# Change Log

Prelude Android SDK Change Log

## [0.3.0] - 2026-01-28

- Updated core SDK to 0.2.0. It includes an updated version of JNA and automatic inclusion of Proguard rules.
- Improved handling of Silent Network Authentication with specific settings per carrier.

## [0.2.5] - 2026-01-22

- Added network-bound DNS resolver for improved Silent Network Authentication (SNA) reliability.

## [0.2.4] - 2025-12-16

- Change default timeouts and retry count for the dispatch signals request. By default, requests now time out after 5 seconds and retries happen automatically up to three times.
- Relax failure conditions for the dispatch signals request.

## [0.2.3] - 2025-09-22

- Added Silent Verification support for Bouygues


## [0.2.2] - 2025-09-02

- Updated to use SDK core version 0.1.2. Support for 16kb memory page included.
- Added new signal to detect when a user is connecting through a VPN application in the device and allow the connection.
- Removed dependencies to make the final library smaller.