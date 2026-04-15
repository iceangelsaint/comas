$TTL    60
$ORIGIN carleton.com.
@    IN    SOA    ns1.carleton.com. admin.carleton.com. (
                  571
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.carleton.com.
@                  60 IN A 192.168.2.21
ns1.carleton.com.   60 IN A 192.168.2.21

brightspace.carleton.com.                60 IN A  3.96.22.70

