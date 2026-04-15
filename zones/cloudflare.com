$TTL    60
$ORIGIN cloudflare.com.
@    IN    SOA    ns1.cloudflare.com. admin.cloudflare.com. (
                  318
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.cloudflare.com.
@                  60 IN A 192.168.2.21
ns1.cloudflare.com.   60 IN A 192.168.2.21

cdnjs.cloudflare.com.                60 IN A  104.17.25.14

