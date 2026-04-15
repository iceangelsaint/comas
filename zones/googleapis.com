$TTL    60
$ORIGIN googleapis.com.
@    IN    SOA    ns1.googleapis.com. admin.googleapis.com. (
                  212
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.googleapis.com.
@                  60 IN A 192.168.2.21
ns1.googleapis.com.   60 IN A 192.168.2.21

ajax.googleapis.com.                60 IN A  142.251.41.74

