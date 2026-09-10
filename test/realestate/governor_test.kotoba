(ns realestate.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [realestate.store :as store]
            [realestate.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Real Estate"})
    (store/register-listing! st {:listing-id "L-1" :client-id "client-1"
                                 :name "listing-042"
                                 :max-authorized-amount 500000
                                 :tenant-screening-completed? true})
    st))

(defn- execute-op [amount]
  {:op :approve-lease-execution :effect :propose :listing-id "L-1"
   :execution-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-authorization-and-screened
  (let [st (fresh-store)
        v (governor/check req {} (execute-op 250000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-authorization-boundary
  (testing "the authorization ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (execute-op 500000) st)]
      (is (:ok? v)))))

(deftest hard-on-execution-exceeds-authorization
  (testing "executing a lease/sale beyond the owner's registered authorization ceiling is unauthorized disposition, not diligent management"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (execute-op 5000000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :execution-exceeds-authorization (:rule %)) (:violations v))))))

(deftest hard-on-tenant-screening-not-completed
  (testing "offering a lease without completed tenant screening is an unscreened placement, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Real Estate"})
      (store/register-listing! st {:listing-id "L-1" :client-id "client-1"
                                   :name "listing-042"
                                   :max-authorized-amount 500000
                                   :tenant-screening-completed? false})
      (let [v (governor/check req {} (assoc (execute-op 250000) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :tenant-screening-not-completed (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-listing
  (let [st (fresh-store)
        v (governor/check req {} (assoc (execute-op 250000) :listing-id "L-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-listing (:rule %)) (:violations v)))))

(deftest hard-on-foreign-listing
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (execute-op 250000) st)]
      (is (:hard? v))
      (is (some #(= :listing-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (execute-op 250000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (execute-op 250000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-authorization-execution-even-at-high-confidence
  (testing "no lease/sale execution above the owner's registered authorization ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-authorization-execution :effect :propose
                                    :listing-id "L-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-security-deposit-disbursement-even-at-high-confidence
  (testing "releasing or refunding a tenant's security deposit always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-security-deposit-disbursement :effect :propose
                                    :listing-id "L-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (execute-op 250000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
