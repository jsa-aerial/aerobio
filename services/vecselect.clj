
{:name "vecselect"
 :path ""
 :func (fn [indices V & _]
         (if (pg/eoi? V)
           (pg/done)
           (mapv V indices)))
 }

