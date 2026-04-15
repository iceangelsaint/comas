$TTL    60
$ORIGIN unpkg.com.
@    IN    SOA    ns1.unpkg.com. admin.unpkg.com. (
                  251
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.unpkg.com.
@                  60 IN A 104.16.122.175
ns1.unpkg.com.   60 IN A 104.16.122.175


