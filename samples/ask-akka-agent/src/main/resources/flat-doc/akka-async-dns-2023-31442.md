# Akka Async DNS resolver has insufficient entropy to protect against DNS poisoning

## Date

2023-05-02

## CVE

CVE-2023-31442

## Description of Vulnerability

Akka’s `async-dns` resolver (used by Akka Discovery in DNS mode and transitively by Akka Cluster Bootstrap) uses predictable DNS transaction IDs when resolving DNS records, making DNS resolution subject to poisoning.

## Severity

[AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:L/A:L/E:P/RL:O/RC:R](https://nvd.nist.gov/vuln-metrics/cvss/v3-calculator?vector=AV:N/AC:H/PR:N/UI:N/S:U/C:L/I:L/A:L/E:P/RL:O/RC:R&version=3.1)

Overall CVSS Score: 4.9

## Impact

If the application performing discovery does not validate (e.g.
via TLS) the authenticity of the discovered service, this may result in exfiltration of application data (e.g.
persistence events may be published to an unintended Kafka broker).
If such validation is performed, then the poisoning constitutes a denial of access to the intended service.

## Resolution

The means of generating DNS transaction IDs was improved to increase effective entropy and validation of DNS responses was hardened.

## Affected versions

* `akka-actor` from 2.5.14 through 2.8.0 (inclusive: all versions before 2.8.1 which include `async-dns`)
* `akka-discovery` through 2.8.0 (all versions before 2.8.1)

## Fixed versions

* `akka-actor` 2.8.1 and later
* `akka-discovery` 2.8.1 and later

## References

* [CVE-2023-31442](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2023-31442)
