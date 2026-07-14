(ns realestate.governor
  "RealEstateGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  lease/sale execution an advisor may propose for a listing. The
  governor never dispatches hardware itself and never executes a
  lease/sale above the owner's registered authorization ceiling.
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor. Task
  twist: a proposed execution amount is an arithmetic ceiling against
  the listing's registered authorization ceiling, and a lease cannot
  be executed until the listing's tenant screening has been
  completed. Both `:max-authorized-amount` and
  `:tenant-screening-completed?` live on the registered listing
  record, not on the proposal.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance   — the property owner must be registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never dispatches hardware and
                             never executes a lease/sale above the
                             registered authorization ceiling; it
                             only gates what the advisor may
                             execute).
    3. listing basis       — a lease-execution proposal must cite a
                             REGISTERED listing belonging to this
                             client.
    4. authorization ceiling — the proposed execution amount must not
                             exceed the listing's registered
                             `:max-authorized-amount` (executing
                             beyond the owner's registered
                             authorization ceiling is unauthorized
                             disposition, not diligent management).
    5. tenant-screening completed — the listing must have
                             `:tenant-screening-completed?` true
                             before any lease can be executed
                             (offering a lease without completed
                             tenant screening is an unscreened
                             placement, not efficient service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-authorization-execution (no lease/sale
                             execution above the owner's registered
                             authorization ceiling without the
                             governor gate).
    7. :op :approve-security-deposit-disbursement (releasing or
                             refunding a tenant's security deposit
                             always requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [realestate.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-authorization-execution
                                     :approve-security-deposit-disbursement})

(defn- hard-violations [{:keys [request proposal]} client-record l]
  (let [{:keys [op execution-amount]} proposal
        execute? (= :approve-lease-execution op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録上限超過の賃貸借/売買を直接実行しない）"})

      (and execute? (nil? l))
      (conj {:rule :unknown-listing :detail "未登録 listing への執行提案は不可"})

      (and execute? l (not= (:client-id l) (:client-id request)))
      (conj {:rule :listing-wrong-client :detail "listing が別 client のもの"})

      (and execute? l (number? execution-amount) (> execution-amount (:max-authorized-amount l)))
      (conj {:rule :execution-exceeds-authorization
             :detail (str "執行額 " execution-amount " > 登録済み授権上限 "
                          (:max-authorized-amount l) "（登録授権上限を超える執行は無許可処分であって注意深い管理ではない）")})

      (and execute? l (not (:tenant-screening-completed? l)))
      (conj {:rule :tenant-screening-not-completed
             :detail "入居審査未完了の listing への賃貸提案は未審査入居であって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `realestate.store/Store`. Pure — never mutates
  the store, never executes a lease/sale above the registered
  authorization ceiling."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        l (some->> (:listing-id proposal) (store/listing store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record l)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
