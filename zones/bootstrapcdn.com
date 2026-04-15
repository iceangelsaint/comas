$TTL    60
$ORIGIN bootstrapcdn.com.
@    IN    SOA    ns1.bootstrapcdn.com. admin.bootstrapcdn.com. (
                  315
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.bootstrapcdn.com.
@                  60 IN A 192.168.2.21
ns1.bootstrapcdn.com.   60 IN A 192.168.2.21

maxcdn.bootstrapcdn.com.                60 IN A  104.18.11.207

