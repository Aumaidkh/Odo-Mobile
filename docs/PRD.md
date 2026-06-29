# Odo — Product Requirements Document

> *Your Car's AI Best Friend*
>
> *"Mechanic se pehle app se poochho. Resale ke time proof dikhao. Kabhi blind mat raho apni car ke baare mein."*

|  |  |
| --- | --- |
| **Version** | v1.0 — MVP |
| **Date** | June 2026 |
| **Status** | Draft — For Review |
| **Platform** | Android (iOS — Phase 2) |
| **Budget** | INR 1,00,000 |
| **Author** | Founder |
| **Confidentiality** | Confidential |
| **Brand** | Odo (formerly "Car CA" working title) — derived from *odometer*; the single number that tells your car's whole story |

---

## 1. Executive Summary

Odo is an AI-powered Android app that acts as a personal financial and health advisor for car owners in India. The app solves three painful, high-cost moments in a car owner's lifecycle:

| Pain Point | Cost of Inaction | Odo Solution |
| --- | --- | --- |
| Mechanic overcharging | Rs. 500–2,000 per visit lost | Bill Scanner + Fairness Check |
| Missed insurance / PUC | Rs. 2,000+ fine or major accident risk | Smart Reminder Engine |
| Poor resale negotiation | Rs. 30,000–80,000 lost | AI Resale Passport |

**Target market:** 30 crore+ registered private vehicles in India, growing 8% YoY. Primary users are urban car owners aged 25–45 who use UPI, have smartphones, and have personally experienced at least one of the above pain points.

---

## 2. Problem Statement

### 2.1 Core User Problems

**Problem 1 — Mechanic Opacity**

Indian car owners have no way to verify if a service bill is fair. Mechanics operate offline with no standardized pricing. A typical oil change ranges from Rs. 1,200 to Rs. 3,500 for the same car in the same city — a 3x variance with zero transparency for the consumer.

**Problem 2 — Documentation Failure**

Insurance lapse, PUC expiry, and missed service intervals are common because reminders are scattered across WhatsApp messages, SMSs, and paper documents. A lapsed insurance policy during an accident can result in total financial ruin for a middle-class family.

**Problem 3 — Resale Information Asymmetry**

When selling a used car, buyers aggressively negotiate down citing unverifiable claims about condition. Sellers have no credible proof of maintenance history, losing Rs. 30,000–80,000 in negotiation. The buyer holds all leverage today.

### 2.2 Why Existing Solutions Fail

| Existing Solution | Gap |
| --- | --- |
| Car's own dashboard | Shows current data only — no history, no cost analysis, no AI |
| Paper service booklet | Easy to lose, cannot be shared digitally, no fairness benchmarking |
| Generic expense apps | Not car-specific, no mechanic benchmarking, no resale feature |
| OEM apps (Maruti, Hyundai) | Brand-locked, no fairness checker, no AI diagnosis, poor UX |

---

## 3. Target Users & Personas

### 3.1 Primary Persona — Rahul, 32, Pune

| Attribute | Detail |
| --- | --- |
| Occupation | Software Engineer, IT company |
| Car | 2020 Maruti Swift VXI, 54,000 km |
| Tech comfort | High — uses GPay, Swiggy, Zepto daily |
| Pain point | Got charged Rs. 3,200 for oil change. Friend paid Rs. 1,800 for same car model. |
| Goal | Sell car in 12–18 months, wants maximum resale value |
| Willingness to pay | Rs. 99–199/month if ROI is tangible and visible |

### 3.2 Secondary Persona — Sunita, 45, Nagpur

| Attribute | Detail |
| --- | --- |
| Occupation | School teacher, family car owner |
| Car | Honda Amaze 2018, husband drives |
| Tech comfort | Medium — uses WhatsApp and YouTube comfortably |
| Pain point | Insurance lapsed once, paid Rs. 5,000 fine. Forgot PUC renewal twice. |
| Goal | Never miss an important deadline again |
| Willingness to pay | Rs. 49/month for reliable reminders alone |

### 3.3 Fleet Persona — Vikram, 38, Mumbai

| Attribute | Detail |
| --- | --- |
| Occupation | Owns 3 Ola-registered cars, driver-managed |
| Pain point | Cannot track per-car expenses. Service history is chaos across 3 WhatsApp chats. |
| Goal | Know exactly which car is profitable, reduce mechanic fraud across fleet |
| Willingness to pay | Rs. 499–999/month for multi-car dashboard |
| Strategic value | 10 Vikrams = Rs. 5,000–10,000/month recurring, very low churn |

---

## 4. Product Scope — MVP vs Phases

| Feature | MVP (Month 1–3) | Phase 2 (Month 4–6) | Phase 3 (Month 7+) |
| --- | --- | --- | --- |
| Car onboarding | Yes |  |  |
| Service log (manual) | Yes |  |  |
| Bill photo scanner (AI) | Yes |  |  |
| Bill fairness check | Basic city average | Locality-level crowdsourced |  |
| Smart reminders | Yes |  |  |
| Document vault | 3 documents | Unlimited |  |
| AI Health Score | Rule-based logic | ML-enhanced |  |
| AI Doctor (chat) |  | Yes — paid only |  |
| Resale Passport |  | Yes |  |
| Per-km cost tracker | Yes — km-based calc |  |  |
| Multi-car support |  | Yes |  |
| Fleet dashboard |  |  | Yes |
| Workshop directory |  |  | Yes |

---

## 5. Feature Specifications

### 5.1 Onboarding Flow

**Goal:** Get the user's car set up in under 90 seconds with enough data to show immediate value on first session.

**Screen 1 — Car Details**

- Make, Model, Year — dropdown only, no freeform typing to reduce errors
- Fuel type: Petrol / Diesel / CNG / Electric
- Current odometer reading — mandatory, used for all km-based calculations
- Purchase year — used for depreciation and resale age estimation

**Screen 2 — Quick History Import (Optional)**

- Prompt: "Upload your last service bill — we will fill the rest"
- AI bill scanner activates immediately — this is the first WOW moment
- Skip option always available — never force

**Screen 3 — Goal Selection**

- "I want to sell my car in next 12 months" — triggers Resale Passport upsell journey
- "I want to track service costs" — surfaces main dashboard and Bill Scanner
- "I want reminders for insurance and PUC" — leads to Document Vault setup

### 5.2 AI Bill Scanner — Core Hook Feature

This is the primary acquisition hook. The moment a user scans their first bill and sees an overpayment flagged, they are retained. **This feature must work flawlessly before any other AI feature ships.**

**User Flow**

- User taps "Scan Bill" from home screen or inside a new service log entry
- Camera opens — user photographs service bill (printed thermal or handwritten)
- Claude Vision API extracts: date, odometer reading, service items, individual costs, total amount, workshop name
- App auto-populates service log entry — user reviews and confirms in one tap
- Fairness overlay shown: "Oil Change: You paid Rs. 2,800. Mumbai average: Rs. 2,100. Possible overpayment: Rs. 700"

**Data Strategy & Cold Start Handling**

- Phase 1: City-level averages seeded from JustDial, Sulekha scrape + manual research for top 10 cities
- Every bill scan adds anonymized service type + amount + city to the shared pool — crowdsourcing flywheel
- Confidence score always shown: "Based on 12 data points in Mumbai" — no false precision
- Less than 5 data points: Show range estimate with explicit low-confidence label

**Accuracy & Honest Limitations**

- Printed thermal bills: 85%+ extraction accuracy expected
- Handwritten bills: Lower accuracy — flag for manual review, do not auto-populate
- Fairness data will be sparse in Tier 2 cities at launch — communicate this clearly to users

### 5.3 Smart Reminder Engine

Reminders are a **retention feature, not a monetization feature.** They keep users active between high-value events like service and resale.

| Trigger | Reminder Message | Channel | Lead Time |
| --- | --- | --- | --- |
| Insurance expiry | Insurance expires in X days — renew now (affiliate link) | Push + WhatsApp | 30d, 7d, 1d |
| PUC expiry | PUC certificate expires in X days — get renewed | Push | 15d, 3d |
| Service due (km) | Service due in 800 km — you are close | Push | At threshold km |
| Service due (time) | Last service was 5 months ago — time for a check? | Push | Monthly |
| Health score drop | Car score dropped to 68 — see what changed | Push | Immediate on change |
| App inactive 7 days | Any new expenses this week? Keep your log updated | Push | 7 days after last open |

### 5.4 AI Health Score

A single number from 0 to 100 representing the overall condition and documentation quality of the car. Psychologically powerful — users want to improve their score, especially before resale.

**Score Composition — Rule-Based for MVP**

- Maintenance regularity: 35 points — services on time, oil changed, tyre age tracked
- Documentation completeness: 30 points — insurance valid, PUC valid, RC uploaded
- Cost efficiency: 20 points — no significant overpayments flagged by bill scanner
- History completeness: 15 points — bills attached, km readings consistent and logical

**Score Bands**

- 85–100: Excellent — strong resale candidate, Resale Passport recommended
- 70–84: Good — minor gaps, specific actions suggested to improve
- 50–69: Fair — multiple issues flagged, improvement roadmap shown
- Below 50: Poor — significant gaps, urgent action items listed

**Monetization Tie-in**

- Free tier: Score number visible, breakdown locked behind Pro
- Resale upsell trigger: "Your score is 74. Cars with 85+ sell for Rs. 40,000 more on average. Here is what to fix."

### 5.5 AI Doctor — Conversational Diagnosis

> **SCOPE LIMIT:** AI Doctor is a first-opinion tool, not a mechanic replacement. It must never claim high accuracy, never advise ignoring a serious symptom, and must always recommend a physical inspection for safety-critical issues (brakes, steering, smoke).

**How It Works**

- Full car history injected into Claude API system prompt before every conversation turn
- User describes symptom in Hinglish — AI responds with context from actual logged data
- Example: "Your oil change was 4,200 km ago — this knocking could be related. Here is what to check first."
- Every response ends with cost range estimate and recommendation to see mechanic

**Accuracy by Query Type**

- Maintenance timing (90%+ accuracy): "When should I change oil?" — deterministic from logged data
- Cost estimates (75–85%): "How much will AC recharge cost?" — shown as range, never exact
- Symptom diagnosis (65–75%): Top 3 causes listed, mechanic visit always recommended
- Safety-critical (brakes, smoke, steering): Always respond "Stop driving — visit mechanic immediately"

**Quick Prompt Chips (Reduce Typing Friction)**

- "Service due kab hai?" / "Is mahine kitna kharch hua?" / "Resale value kya hai?" / "Koi alert hai?"

### 5.6 Resale Passport

A shareable digital document that proves a car's maintenance history to a potential buyer. This is the highest-value paid feature and the primary one-time revenue driver.

**Contents**

- Car details: Make, model, year, km, fuel type, purchase year
- AI Health Score with full breakdown
- Complete service timeline — each entry shows Verified (bill uploaded) or Self-Reported
- Document validity: Insurance, PUC, RC status
- Km consistency check: AI flags any odometer anomalies or impossible progressions
- Self-declared accident history — clearly labelled as self-declared, not verified

**Trust & Anti-Fraud Layers**

- Verified badge requires bill photo — manual entries without photo are labelled Unverified
- Km anomaly detection: If readings go backwards or jump suspiciously, flagged in report
- Honest disclaimer on Passport: "History is as reported by owner. Odo verifies bill uploads only."

**Monetization**

- One-time unlock: Rs. 249 per Passport generation — not subscription
- Rationale: High-intent one-time moment. Subscription friction is wrong at point of car sale.
- Shareable as PDF download or web link — buyer does not need to install the app

### 5.7 Per-Km Cost Tracker

Calculates total ownership cost per kilometer driven using only service log km readings. No manual fuel logging required — solving the friction problem validated during product review.

**Data Source**

- Km readings from service log entries — odometer reading is a mandatory field in every log
- Total logged expenses divided by km delta between earliest and latest entry
- Fuel cost estimated: car fuel type + current city fuel price (fetched weekly from public data)

**Output Examples**

- "Your car costs Rs. 4.2/km (maintenance) + Rs. 8.1/km (estimated fuel) = Rs. 12.3/km total"
- "Mumbai Swift average: Rs. 11.8/km — you are 4% above average"
- "Your 22 km daily commute costs approximately Rs. 270/day or Rs. 5,940/month"

---

## 6. Monetization Strategy

### 6.1 Pricing Tiers

| Tier | Price | Key Features Included | Target User |
| --- | --- | --- | --- |
| Free | Rs. 0 | Manual log 20 entries/month, 3 bill scans, basic reminders, 3 documents, health score number only | New users, low-intent |
| Pro | Rs. 149/month | Unlimited logs, unlimited bill scans, AI Doctor, full health breakdown, analytics dashboard | Active car owners |
| Resale Passport | Rs. 249 one-time | Shareable PDF and web link, verified history report, buyer-facing document | Selling their car in 0–6 months |
| Fleet (Phase 3) | Rs. 999/month | Up to 10 cars, per-car P&L, expense dashboard, priority support | Cab owners, small fleets |

### 6.2 Affiliate Revenue Streams

| Partner Type | Example Partners | Trigger Event | Commission Estimate |
| --- | --- | --- | --- |
| Insurance renewal | Acko, Digit, Tata AIG | 30 days before expiry reminder | Rs. 200–500 per policy |
| Car service booking | GoMechanic, MyTVS | When service due alert fires | Rs. 100–300 per booking |
| Car loan refinance | HDFC, Bajaj Finance | When user adds loan details | Rs. 500–2,000 per lead |

### 6.3 Paywall Placement Strategy

The Bill Scanner is the primary conversion trigger. Free users get 3 scans. On the 3rd scan, if overpayment is detected, the paywall appears: *"You just saved Rs. 700. Upgrade to Pro to scan every bill, every time."* This is the highest-intent moment for conversion.

### 6.4 6-Month Revenue Projection

| Month | Downloads | Active Users | Pro Subscribers | Passport Sales | Affiliate Rev. | Total MRR Est. |
| --- | --- | --- | --- | --- | --- | --- |
| 1–2 | 500 | 200 | 0 | 0 | Rs. 0 | Rs. 0 |
| 3 | 1,500 | 600 | 30 | 10 | Rs. 2,000 | Rs. 6,970 |
| 4 | 2,500 | 1,000 | 70 | 20 | Rs. 5,000 | Rs. 20,430 |
| 5 | 4,000 | 1,600 | 130 | 35 | Rs. 9,000 | Rs. 37,370 |
| 6 | 6,000 | 2,400 | 220 | 60 | Rs. 15,000 | Rs. 62,780 |

> **Note:** These are target projections, not guarantees. Actual numbers depend on execution quality, ad performance, and retention. A kill signal is less than 100 MAU at Month 3 or less than 2% paid conversion by Month 4.

---

## 7. Technical Architecture

### 7.1 Stack Decisions

| Layer | Technology | Rationale |
| --- | --- | --- |
| Android App | Kotlin + Jetpack Compose | Founder expertise — fastest build path |
| iOS (Phase 2) | Kotlin Multiplatform (KMP) | Shared business logic, leverages existing KMP experience |
| Backend + DB | Supabase (Postgres + Auth + Storage) | Free tier covers MVP, no DevOps overhead, instant setup |
| AI — Bill Scanner | Claude Vision API (claude-sonnet-4-6) | Best-in-class OCR + reasoning on mixed bill formats |
| AI — Doctor Chat | Claude Haiku API | Cheap and fast for conversational use — approx Rs. 0.02/message |
| AI — Health Score | Rule-based logic (MVP) | Deterministic, zero API cost, upgradeable to ML in Phase 2 |
| Push Notifications | Firebase Cloud Messaging (FCM) | Free, reliable, industry standard for Android |
| Payments | Razorpay | India-first, UPI support, simple SDK integration |
| Analytics | PostHog | Free tier, privacy-first, no Google dependency |

### 7.2 AI Cost Model at Scale

| Feature | Model | Cost Per Call | At 1,000 MAU | Monthly Cost Est. |
| --- | --- | --- | --- | --- |
| Bill Scanner | Claude Sonnet Vision | ~Rs. 0.15/scan | 500 scans/day | ~Rs. 2,250 |
| AI Doctor Chat | Claude Haiku | ~Rs. 0.02/message | 2,000 messages/day | ~Rs. 1,200 |
| Health Score | Rule-based | Rs. 0 | — | Rs. 0 |
| **Total AI Infrastructure** | — | — | — | **~Rs. 3,500/month** |

At 220 Pro subscribers (Month 6 target): Revenue Rs. 32,780 from subscriptions. AI cost Rs. 3,500. AI cost as % of revenue: **10.7%**. Healthy margin even before affiliate revenue.

---

## 8. Go-To-Market Strategy

### 8.1 Budget Allocation — Rs. 1,00,000

| Category | Amount | What It Buys |
| --- | --- | --- |
| Google Play Store account | Rs. 2,000 | One-time developer account fee |
| Domain + Supabase Pro (1 year) | Rs. 12,000 | Higher DB limits, custom domain, email |
| Content creation (6 months) | Rs. 18,000 | Reels and Shorts — mechanic expose content format |
| Meta Ads — targeted | Rs. 50,000 | Car owner audiences, Tier 1 cities, "Mechanic ne loot liya" hook creative |
| Car community seeding | Rs. 8,000 | Team-BHP forum, Facebook groups, WhatsApp group admin outreach |
| Contingency and misc | Rs. 10,000 | Bug fixes, App Store registration for Phase 2 |

### 8.2 Month-by-Month Execution Plan

| Month | Focus Area | Key Actions | Success Metric |
| --- | --- | --- | --- |
| 1–2 | Build | MVP: Onboarding + Service Log + Bill Scanner + Reminders. Internal testing only. | App in Play Store internal testing |
| 3 | Seed | 10 real users recruited manually. 5 service center partnerships for word-of-mouth. | 50 downloads, 1 paid user |
| 4 | Content | 3 Reels/week: "Bill Scanner caught Rs. 700 overcharge" format. Post to car groups. | 500 downloads, 10% Week 4 retention |
| 5 | Paid Push | Rs. 25,000 Meta ads. Target: car owners 28–45, Tier 1 cities, petrol/diesel interests. | 2,000 downloads, 30 paid users |
| 6 | Convert | AI Doctor launch for Pro users. Resale Passport launch. Insurance affiliate live. | 100+ paid users, first affiliate revenue |

### 8.3 Zero-Cost Distribution Channels

**Service Center Partnerships**

- Pitch to workshops: "App mein aapka workshop listed hoga as Trusted Partner — completely free"
- Workshop recommends app to customers at point of highest intent — just paid a bill, wants to track it
- Target: 20 workshops across 3 cities in Month 1–2 before paid ads

**Content Virality Play**

- Hook format: "Mechanic ne Rs. 1,100 zyada charge kiya — proof hai mere paas" — Reels format
- Target communities: Facebook car owner groups, Team-BHP forum, Reddit r/india
- Hinglish content — higher engagement for target demographic aged 28–45

**Resale Passport Organic Virality**

- Every shared Passport link is a non-user seeing the product — zero cost impression
- Passport footer: "Track your car with Odo — free download on Play Store"
- Each car sale = potentially 3–5 new users seeing the product through the buyer and their network

---

## 9. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation Strategy |
| --- | --- | --- | --- |
| Bill fairness data sparse at launch | High | Medium | Seed with scraped data. Always show confidence score. Never show false precision. |
| Logging fatigue — users stop logging | High | High | Bill scan = near-zero effort entry. Only odometer reading is mandatory per log. |
| Resale Passport trust gap — fake data | Medium | High | Bill photo mandatory for Verified badge. AI km consistency check on all entries. |
| AI Doctor gives wrong or harmful advice | Medium | High | Strict scope limits. Safety-critical symptoms always redirect to mechanic immediately. |
| Low paid conversion below 5% | Medium | High | Gate Bill Scanner at 3 free scans. Paywall at moment of highest value — overpayment detected. |
| Solo founder bandwidth constraint | High | High | Strict MVP scope. No feature creep. Ship by Month 2. Iterate Month 3 onwards only. |
| Competitor OEM app copies feature | Low | Medium | Speed to market + multi-brand positioning. OEM apps are permanently brand-locked. |

---

## 10. Success Metrics & Decision Gates

### 10.1 North Star Metric

> **Number of bills scanned per month.**
> High bill scans = active users + growing fairness database + acquisition stories for content.

### 10.2 Key Metrics by Phase

| Metric | Month 3 Target | Month 6 Target | Kill Signal |
| --- | --- | --- | --- |
| Monthly Active Users | 300 | 2,000 | Less than 100 at Month 3 |
| Day-30 Retention | 25% | 35% | Less than 15% at Month 3 |
| Bills Scanned per MAU | 2 per user | 4 per user | Less than 1 per user at Month 3 |
| Free-to-Paid Conversion | 3% | 10% | Less than 2% at Month 4 |
| Paid Monthly Churn | Less than 15% | Less than 8% | Greater than 20% at Month 4 |
| Net Promoter Score | Greater than 30 | Greater than 50 | Less than 10 at Month 3 |

### 10.3 Decision Gate — Month 3 Review

At the end of Month 3, evaluate the product with the following binary decisions:

- **IF** Day-30 retention exceeds 25% **AND** bill scans growing week-over-week → Continue full plan as defined.
- **IF** retention below 15% → Pause all paid marketing. Conduct 20 user interviews. Identify exact drop-off point.
- **IF** paid conversion below 2% by Month 4 → Revisit paywall placement. Test Rs. 99/month price point.
- **IF** zero traction after Rs. 50,000 in ads → Pivot entirely to fleet B2B model targeting Vikram persona.

---

## 11. Explicitly Out of Scope for MVP

The following features are acknowledged but deliberately excluded from MVP to maintain execution focus. Every item excluded is a conscious decision, not an oversight.

| Feature | Why Excluded | When to Revisit |
| --- | --- | --- |
| iOS app | KMP adds 4–6 weeks — validate on Android first before cross-platform investment | Month 5+ if Android shows clear traction |
| AI Doctor | Needs accumulated history data to be useful — chicken-and-egg at launch | Month 4 after data accumulation |
| Workshop directory | Requires supply-side onboarding effort — operational, not product work | Phase 3 with dedicated business development |
| Manual fuel log | Validated as high-friction, low-value by founder's own experience | Only if UPI SMS auto-detection is built |
| Social and community features | Distraction from core utility — premature at MVP stage | Phase 3 if retention is strong |
| Web application | Car owners are mobile-first — app is the correct channel | Phase 3 |
| Accident and insurance claims | Regulatory complexity far beyond current scope and budget | Evaluate as separate product vertical |

---

## 12. Open Questions

| # | Open Question | Owner | Deadline |
| --- | --- | --- | --- |
| 1 | What minimum data points are needed for bill fairness check to show confidence? Define threshold before dev. | Founder | Before dev start |
| 2 | Resale Passport: Rs. 249 one-time vs included in Pro subscription? Run pricing experiment at Month 3. | Founder | Month 3 |
| 3 | Legal review: Can we use "overpaid" language without mechanic permission? Disclaimer wording needed. | Legal advisor | Month 2 |
| 4 | WhatsApp Business API for reminders: Cost, feasibility, and approval timeline? | Founder | Month 2 |
| 5 | Insurance affiliate: Acko vs Digit vs Tata AIG — which offers best commission rate and integration UX? | Founder | Month 3 |
| 6 | KYC or third-party verification for Resale Passport to increase buyer trust — cost vs benefit? | Founder | Month 4 |

---

## Appendix — Competitive Landscape

| Product | Strengths | Weaknesses | Odo Advantage |
| --- | --- | --- | --- |
| CarDekho / Droom | Large used car marketplace, strong brand | Transaction-only, no ownership lifecycle tracking | Full ownership lifecycle, not just transaction |
| OEM Apps (Maruti, Hyundai) | Deep car data integration | Brand-locked, no AI, poor UX, no fairness check | Multi-brand, AI-first, independent |
| Fuelio (Global app) | Good fuel tracking interface | No India pricing data, no AI, no resale feature | India-first + bill fairness + AI |
| GoMechanic App | Service booking available | No ownership tracking, no history, no AI | Tracking + AI vs booking only |
| Paper service booklet | Trusted by buyers historically | Cannot be shared, easily lost, no intelligence | Digital + verified + shareable |

---

*Odo PRD v1.0 — End of Document — Confidential*
