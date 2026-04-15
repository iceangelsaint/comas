$TTL    60
$ORIGIN microsoft.com.
@    IN    SOA    ns1.microsoft.com. admin.microsoft.com. (
                  670
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.microsoft.com.
@                  60 IN A 192.168.2.21
ns1.microsoft.com.   60 IN A 192.168.2.21

www.microsoft.com.                60 IN A  20.112.250.133

