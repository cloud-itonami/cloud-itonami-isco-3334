(ns realestate.store
  "SSoT for the ISCO-08 3334 independent real estate & property
  management practice actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a
  property-condition documentation robot performs walkthrough
  photographing, condition-report assembly and physical filing under
  this advisor/governor pair, which never dispatches hardware itself
  and never executes a lease/sale above the owner's registered
  authorization ceiling). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client  — a registered property owner (:client-id, :name)
    listing — a registered property listing {:listing-id :client-id
              :name :max-authorized-amount number
              :tenant-screening-completed? boolean}.
              `:max-authorized-amount` is the registered authorization
              ceiling a proposed lease/sale execution amount must not
              exceed — executing a lease/sale beyond the owner's
              registered authorization ceiling is unauthorized
              disposition, not diligent management.
              `:tenant-screening-completed?` records whether tenant
              screening has been completed for this listing — offering
              a lease without completed tenant screening is an
              unscreened placement, not efficient service.
    record  — a committed operating record (an executed lease/sale) —
              written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (listing [s listing-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-listing! [s l])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (listing [_ listing-id] (get-in @a [:listings listing-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-listing! [s l]
    (swap! a assoc-in [:listings (:listing-id l)] l) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :listings {} :records [] :ledger []}
                                   seed)))))
