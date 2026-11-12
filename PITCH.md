# Galla: an AI munim for small shopkeepers

**Live app:** https://chhotu.onrender.com  |  **Code:** https://github.com/MehakDogra2005/Chhotu
**Team:** Mehak Dogra 
          Suhani Goyal

## The problem
A small shopkeeper keeps the business in a notebook and in their head: who owes how much (udhaar), what is left in stock, and whether the month made money. Typing into an app is slow, most apps speak English, and nobody has time to learn dashboards while a customer waits.

## Our solution
**Galla** is a voice-first assistant for the shop, called **Munim Ji** (the traditional shop accountant). The shopkeeper just talks, in their own language, and Munim Ji does the bookkeeping.

- **Say it, it is done:** "Rahul ne 500 de diye" records a payment. "Rohan ne 2 Chips aur 3 Soft Drink udhaar mein liye" writes a bill into his udhaar and reduces the stock. "20 Chocolate aa gaye" adds stock, or creates a new item.
- **Safe by design:** every change is shown first and happens only after the shopkeeper says "haan". Two customers with the same name? It asks which one, or picks by the amount said.
- **Ask anything about the shop:** "Who owes me the most?", "Which supplier do I owe?", "Give me business insights". Answers come from the shop's own data.
- **No dodging:** tap any customer in Udhaar to see every item and bill behind what they owe.
- **10 Indian languages:** Hindi, English, Tamil, Telugu, Kannada, Malayalam, Marathi, Bengali, Gujarati and Punjabi. It listens in the language spoken and answers in the same one, in text and voice. The whole website and the shop's product and customer names switch language too.
- **Also:** inventory with low-stock alerts, restock lists, profit and loss with a business advisor, payment reminders, customer orders, and it installs on a phone like an app.

## How it works
1. **Gnani Vachana** turns speech into text and speaks the reply back.
2. A keyword engine handles common commands instantly. Anything else goes to **Gemini**, which finds the language and the request, or answers from a summary of the shop's data.
3. Changes go through the confirm step and are saved in an append-only ledger with an audit log.

**Built with:** Java 17 / Spring Boot, React, Tailwind, H2 or PostgreSQL, Gnani Vachana, Google Gemini, Docker on Render.

## Try it
Log in with phone **9999900001** and code **123456** (or **9999900002**, same code), tap the mic and say "Rahul Gupta 719 ne 500 de diye".

## Honest limits
SMS and email login codes are not available on the free host, so two demo logins and Google login are provided. Free-tier AI quotas can slow some answers. Sample data resets when the app is redeployed.
