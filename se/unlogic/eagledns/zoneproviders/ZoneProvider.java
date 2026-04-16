package se.unlogic.eagledns.zoneproviders;

import java.util.Collection;
import se.unlogic.eagledns.SecondaryZone;
import se.unlogic.eagledns.plugins.Plugin;

public interface ZoneProvider extends Plugin {
   Collection getPrimaryZones();

   Collection getSecondaryZones();

   void zoneUpdated(SecondaryZone var1);

   void zoneChecked(SecondaryZone var1);
}
