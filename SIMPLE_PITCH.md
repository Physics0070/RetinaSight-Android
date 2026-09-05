# RetinaSight AI — The Simple Pitch

No jargon. For anyone: a health officer, a business judge, your grandmother.

---

# THE 30-SECOND VERSION

> "Diabetes slowly damages the back of the eye. If you catch it early, it's
> treatable. If you catch it late, the person goes blind.
>
> In villages there's nobody qualified to look at the eye photos. So the photos
> get sent away, and the answer comes back **weeks later** — when the patient is
> long gone.
>
> We put the expert **inside the phone**. The phone looks at the eye photo and
> answers in a few seconds, with no internet, and tells the patient in their own
> language whether they need a doctor — **while they're still sitting there.**"

---

# THE STORY (tell it like this)

A health worker sets up in a village. Two hundred people with diabetes queue up.
She photographs the back of each person's eye.

**Then what?**

Right now: those 200 photos get sent to a city hospital. An eye doctor looks at
them — eventually. Weeks pass. The report comes back. Nobody can find half those
patients again. The ones who needed treatment never got the message.

**That gap is where people go blind from something curable.**

Our app removes the gap. The phone gives the answer in seconds, on the spot.

---

# WHAT ACTUALLY HAPPENS, STEP BY STEP

Explained the way you'd explain it to someone who has never used a computer.

### 1. Ask permission first

The app won't let you take a photo until the patient has agreed. Not a rule on
paper — the button literally doesn't work until you tick the box.

> **Why it matters:** it's someone's body and someone's health record.

### 2. Note who it is, and which eye

Name, age, and *right eye or left eye*.

> **Why the eye matters:** one eye can be perfectly healthy while the other is in
> trouble. A result that doesn't say which eye is useless to a doctor.

### 3. Take the photo

Either through the camera, or pick a photo already on the phone.

### 4. Check the photo is good enough — *before* looking at it

This is the step people don't expect, and it's the most important one.

The AI is like a very experienced eye specialist who **cannot say "I can't see
properly."** Show it a blurry photo and it will still confidently give you an
answer — a wrong one.

So first the phone checks four things itself:

- **Is it sharp,** or did the hand shake?
- **Is the light right,** or is it too dark or washed out?
- **Is the eye centred,** or half out of frame?
- **Is this even an eye?**

If something's wrong it says exactly what to fix — *"hold steady and let the
camera focus"*, *"move somewhere darker so the pupil opens"* — so the worker can
retake it in ten seconds while the patient is still in front of them.

> **Simple analogy:** like a passport photo booth that says *"you blinked, try
> again"* instead of printing a bad photo.

### 5. Prepare the photo the exact same way, every time

The phone crops to the round part of the eye and shrinks it to a standard size.

> **Why:** the AI learned from photos prepared in one particular way. If you hand
> it a photo prepared differently, it still answers — just less accurately, and
> **you'd never know**. So we make the phone prepare photos identically, down to
> the arithmetic.
>
> **Analogy:** a doctor trained to read X-rays taken from the front. Hand him a
> side view and he'll still say something — it just won't be reliable.

### 6. The AI looks at it

The model has studied **thousands of real retina photographs** where eye doctors
had already written down the answer. From those, it learned what damage looks
like.

It gives back three things:

- **A grade** — from "nothing found" to "advanced damage", five levels
- **How sure it is**
- **A heat map** — a coloured patch showing *which part of the eye it was looking
  at* when it decided

> **The heat map matters:** it's the AI showing its working. A doctor can glance
> at it and see whether it was looking at a real problem area or at something
> irrelevant. Without it, you're just asked to trust a number.

All of this happens **inside the phone**. Nothing is sent anywhere. It works
with the phone in aeroplane mode.

### 7. Say what to do about it

The result is turned into plain advice — *"see an eye doctor within one month"* —
and **read out loud** in the patient's own language.

> **Why out loud:** many patients can't read. Voice is the only channel that
> reaches them.

The spoken message is deliberately short — three things only: **what was found,
how sure we are, what to do.** It doesn't read the whole screen out; that would
waste the one channel that person has.

### 8. Save it, and share it only if you choose

Everything is saved on the phone so the worker can show a patient's history on
the next visit, even with no signal.

If a clinic is connected, finished results upload automatically when the phone
next gets internet.

> **Important:** the eye photograph itself **never leaves the phone.** Only the
> result. A photo of someone's eye is personal; the grade is what the clinic
> needs.

---

# WHY OURS IS DIFFERENT (three things, plainly)

**1. It works with any camera.**
The main product doing this today only works with *their own* expensive camera.
Ours works with any eye-camera a clinic already owns.

**2. It works with no internet at all.**
Not "works better offline" — it has **no ability to use the internet** for the
medical part. That's not a claim, it's how it's built.

**3. We measured things nobody bothers to measure.**
How many seconds per patient. How much battery per patient. **How many patients
one full charge covers.** Whether the phone overheats during a long camp.

> A health officer buying this doesn't ask about accuracy percentages. They ask
> *"will it last the whole camp?"* Nobody publishes that. We measure it.

---

# THE HONEST PARTS (say these yourself)

**"You still need an eye camera."**
You cannot photograph the back of an eye with a phone camera alone — it's
physically impossible, you'd just get a picture of the surface. A special lens or
camera is needed. Clinics already have these. **We're the expert, not the
camera.**

**"It's a screening helper, not a doctor."**
It tells you *who needs to see a doctor*. It never replaces one. Every result
says so.

**"It isn't approved as a medical device yet."**
It's a research and screening project. That's an honest stage to be at.

---

# SIMPLE ANSWERS TO SIMPLE QUESTIONS

**"Does it need internet?"**
No. The medical part cannot use the internet at all. Sharing results with a
clinic is optional and separate.

**"How long does it take?"**
A few seconds per eye — while the patient is still sitting there.

**"How accurate is it?"**
It agrees closely with eye doctors on the test images, and catches about 94 out
of every 100 people who genuinely need referral. We're working to push that
higher, because missing someone is far worse than a false alarm.

**"What if it's wrong?"**
Anyone flagged goes to a real doctor — so a false alarm costs one visit. The
danger is missing someone, which is why we check photo quality first and show
the heat map, and why we'd rather over-refer than under-refer.

**"Who uses it?"**
Health workers and technicians at rural eye camps and small clinics — people who
already have a camera but no eye specialist.

**"Who pays?"**
Government eye-health programmes, NGOs running camps, small clinics. Not the
patient.
