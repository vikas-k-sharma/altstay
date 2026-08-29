# R0 Gate Run Sheet

**One partner, 60 minutes.** Print this, or keep it on a second screen. Use one copy per session,
and run the whole thing twice — once per owner.

The gate is one question, and you are not allowed to ask it:

> **Do they, unprompted, ask when they can have it?**

Everything on this page exists to keep you quiet long enough to find out.

Derived from [`.plans/phase-3-validation.md`](../.plans/phase-3-validation.md) §5.1–§5.5 and §6.
That file is the specification; this one is the thing you hold during the hour. If they disagree,
the plan wins.

---

## Before they sit down

- [ ] **Confirm the API key is off the free tier.**
      Verified 2026-08-29: the key on this machine is **free-tier, capped at 5 requests per minute**
      on `gemini-2.5-flash`. A steady conversational pace stays under it; a **burst does not** —
      clicking through the suggested-question chips, a run of short rapid questions, or a Retry
      after a slow reply. There is no retry layer to absorb it: `ChatClientConfig` pins
      `retryOptions(attempts(1))`, so a 429 fails on the first attempt and renders to the owner as
      *"The concierge is offline for a moment."* Act 2 is designed to produce exactly those bursts.

- [ ] **Check their rules fit in 20,000 characters.**
      Over the cap the composer is disabled outright and chat is blocked entirely. Paste their
      rulebook and read the counter **before** they arrive, not in front of them.

- [ ] **One capture directory per partner.**
      Set `ALTSTAY_CAPTURE_DIR` in the `npm run dev` terminal and give this partner their own
      folder. The browser sends no `x-altstay-session` header, so both partners on one day would
      append to the same `local-<date>.jsonl`. Restart `npm run dev` between sessions, and rename
      the file the moment the session ends.

- [ ] **Walk [`dev-runbook.md`](../.plans/dev-runbook.md) §3 and §4 on this machine.**
      Both servers up, health green, one live answer, one escalation, one live rule edit. Then
      delete the dry-run capture file so it cannot be mistaken for their data.

- [ ] **Load their rules, not a preset.**
      The hypothesis is *owners will let an AI answer their guests, if they control what it knows.*
      A preset tests our writing instead. Swap the property name too — the shipped presets carry two
      real hostel chains' names, which is a poor thing for a third owner to be looking at.

---

## The hour

| Act | Minutes | Clock | What happens |
| --- | --- | --- | --- |
| **0 · Consent** | 1 | 00:00 | Read the line below out loud. Wait for an actual yes |
| **1 · Cold** | 10 | 00:01 | They read the knowledge base built from their own rules. They talk. You don't |
| **2 · They play the guest** | 20 | 00:11 | They type what their guests actually ask. **Give no examples** |
| **3 · They play the owner** | 15 | 00:31 | They edit the rules live and re-ask |
| **4 · Debrief** | 14 | 00:46 | The six questions below, in order, in these words |

### Act 0 — Consent

> *"I'm recording the questions you type so I can fix what it gets wrong. Nothing leaves my
> laptop."*

Wait for an actual yes. In the same breath, ask whether you may keep their house rules and publish
their reactions under a pseudonym.

### Act 1 — Cold

They read the knowledge base you built from their own rules. They talk. **You do not.**

> The first unguided reaction is the most valuable thing said all hour. Write it verbatim, pauses
> included.

### Act 2 — They play the guest

They type what their guests actually ask. **Give no examples.** The unprompted distribution of
questions is the entire point of the act, and one suggestion from you contaminates it.

> Silence is fine. Let it stretch.

### Act 3 — They play the owner

They edit the rules live and re-ask. This is *"you control what it knows"* being tested rather than
asserted, and it is the act that separates a trust ceiling from a knowledge gap.

Every edit writes a new `kb` record to the capture file. What they chose to change is evidence in
its own right.

### Act 4 — Debrief

The six questions below, in order, in these words. Write the answers verbatim — not your summary of
them.

---

## Debrief — ask these exactly

1. Would you switch this on for real guests **this week**? If not, what would have to be true?
2. Which answer worried you most, and what would it have cost you?
3. What did you want to change in the rules that you couldn't?
4. What do guests ask that you'd never want a machine to answer?
5. What do they ask that you're sick of answering yourself?
6. If this existed tomorrow, what would you pay for it, and per what?

---

## Listen for these two

**Unprompted only. Record word for word. Never solicit.**

> ### "When can I have this?"
>
> The R0 metric itself. It counts **only** if they raise it — asking *"so, would you want this?"*
> destroys the measurement, and it cannot be undone afterwards.

> ### "Can it also tell them if we have space?"
>
> Rung 2 pulling itself forward. The **strongest single signal** available in this phase: if it
> appears, read it as pressure to compress R1.

### The verdict table

| What happened | Verdict |
| --- | --- |
| Both ask unprompted; ≥1 mentions availability | **GO.** Start R1, and treat the availability ask as pressure to compress it |
| Both ask, neither mentions availability | **GO,** R1 as written |
| One asks, one is lukewarm | **Not a result.** n=2 with a split is noise — run a third session before deciding |
| Both lukewarm for **fixable** reasons (specific wrong answers, missing rules) | **Conditional.** One hardening cycle, one re-run with the same owners. If still lukewarm, it's the kill criterion wearing a disguise |
| Both say *"I'd still want to answer myself"* | **KILL as specified.** Pivot to owner-facing ops tooling. Do not start R1 |

---

## The interviewer rule

**Do not defend a wrong answer.** No *"ah, that's because the knowledge base doesn't have…"*. Their
reaction to a bad answer **is** the data — it is precisely the data the kill criterion is made of.
Write it down and move on.

Afterwards, separate the two failures carefully:

- **"The concierge can't do this"** is the trust ceiling, and it kills.
- **"The rules didn't say"** is a knowledge gap — and if act 3 fixed it live while they watched,
  that is a point **for** the hypothesis, not against it.

---

## Within the hour after they leave

Rename the capture to `partner-<a|b>-<date>.jsonl` and write the notes file while their tone of
voice is still in your head. Annotate every turn with `verdict`, `ownerNote` and `keep` per
[`phase-3-validation.md`](../.plans/phase-3-validation.md) §3.4 — an unannotated transcript is a
log, not evidence, and annotation is the step that turns it into a regression suite.

Delete the synthetic fixtures in `.plans/phase-3-transcripts/` once real ones exist. Real and
fabricated transcripts must never sit side by side in that directory.

---

*A styled, printable version of this page is published as an Artifact:
<https://claude.ai/code/artifact/82f821c6-f21d-4df3-9ddd-bdd14486e5ee>. This file is the source of
truth; update both together.*
