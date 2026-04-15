$TTL    60
$ORIGIN w3schools.com.
@    IN    SOA    ns1.w3schools.com. admin.w3schools.com. (
                  50
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.w3schools.com.
@                  60 IN A 13.248.240.136
ns1.w3schools.com.   60 IN A 13.248.240.136

www.w3schools.com.                60 IN A  192.229.173.207

