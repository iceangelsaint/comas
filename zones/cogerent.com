$TTL    60
$ORIGIN cogerent.com.
@    IN    SOA    ns1.cogerent.com. admin.cogerent.com. (
                  111
             604800
              86400
            2419200
              60 )

@                  IN NS ns1.cogerent.com.
@                  60 IN A 216.181.246.15
ns1.cogerent.com.   60 IN A 216.181.246.15

comas.cogerent.com.                60 IN A  134.117.226.23

comas-its.cogerent.com.                60 IN A  134.117.226.22

comas-its3.cogerent.com.                60 IN A  134.117.226.25

comas-its4.cogerent.com.                60 IN A  134.117.226.21

comas-its2.cogerent.com.                60 IN A  134.117.226.24

comas-home.cogerent.com.                60 IN A  216.181.246.15

comas-live1.cogerent.com.                60 IN A  174.89.129.170

comas-live2.cogerent.com.                60 IN A  174.89.129.170

comas-live3.cogerent.com.                60 IN A  174.89.129.170

comas-local.cogerent.com.                60 IN A  192.168.87.10

comas-live4.cogerent.com.                60 IN A  174.89.129.170

comas-master.cogerent.com.                60 IN A  134.117.226.20

