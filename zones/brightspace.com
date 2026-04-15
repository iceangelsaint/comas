$TTL    60
$ORIGIN brightspace.com.
@    IN    SOA    ns1.brightspace.com. admin.brightspace.com. (
                  226
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.brightspace.com.
@                  60 IN A 192.168.2.21
ns1.brightspace.com.   60 IN A 192.168.2.21

s.brightspace.com.                60 IN A  18.67.17.46

