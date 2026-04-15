$TTL    60
$ORIGIN blindsidenetworks.com.
@    IN    SOA    ns1.blindsidenetworks.com. admin.blindsidenetworks.com. (
                  787
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.blindsidenetworks.com.
@                  60 IN A 192.168.2.21
ns1.blindsidenetworks.com.   60 IN A 192.168.2.21

konekti.blindsidenetworks.com.                60 IN A  34.160.150.230

