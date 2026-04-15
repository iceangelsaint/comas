$TTL    60
$ORIGIN exlibrisgroup.com.
@    IN    SOA    ns1.exlibrisgroup.com. admin.exlibrisgroup.com. (
                  656
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.exlibrisgroup.com.
@                  60 IN A 192.168.2.21
ns1.exlibrisgroup.com.   60 IN A 192.168.2.21

ocul-crl.primo.exlibrisgroup.com.                60 IN A  216.147.222.65

