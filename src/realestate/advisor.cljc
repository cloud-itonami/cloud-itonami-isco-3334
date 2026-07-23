(ns realestate.advisor
  "Real Estate Advisor — the advisor named in this repository's
  README, proposing a real-estate operation (execute a lease/sale,
  approve an over-authorization execution, approve a security-deposit
  disbursement) from a property listing, tenant/buyer request and
  management agreement. Swappable mock/llm; the advisor ONLY proposes
  — `realestate.governor` checks the authorization ceiling and
  tenant-screening completion independently and always escalates
  over-authorization-execution and security-deposit-disbursement
  decisions. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-lease-execution|:approve-over-authorization-execution|:approve-security-deposit-disbursement
               :effect :propose :listing-id str :execution-amount
               number :stake kw :confidence n :rationale str}. The
  authorization-ceiling and tenant-screening state live on the
  registered listing record itself (see `realestate.store`), not on
  the proposal."
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake listing-id execution-amount] :as request}]
  {:op op
   :effect :propose
   :listing-id listing-id
   :execution-amount execution-amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a real-estate advisor. Given a request, propose an :op, the
   :listing-id and :execution-amount, an honest :confidence and a
   :stake. Never propose an execution amount beyond the listing's
   registered authorization ceiling, or a lease execution for a
   listing whose tenant screening is incomplete — the governor checks
   both against the registered listing record. Over-authorization
   executions and security-deposit disbursements always require human
   sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
