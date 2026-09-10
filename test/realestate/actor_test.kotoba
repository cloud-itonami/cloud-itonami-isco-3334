(ns realestate.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [realestate.actor :as actor]
            [realestate.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Real Estate"})
    (store/register-listing! st {:listing-id "L-1" :client-id "client-1"
                                 :name "listing-042"
                                 :max-authorized-amount 500000
                                 :tenant-screening-completed? true})
    st))

(deftest commits-a-within-authorization-screened-execution
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-lease-execution :stake :low
                 :listing-id "L-1" :execution-amount 250000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-authorization-execution
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-lease-execution :stake :low
                 :listing-id "L-1" :execution-amount 5000000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-authorization-execution-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-authorization-execution :stake :low
                 :listing-id "L-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
