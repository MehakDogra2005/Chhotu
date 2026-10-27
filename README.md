# Dukaan Saathi

Voice-first business assistant for small shopkeepers. So far: login / sign-up and the Home dashboard.

## Run it

Double-click **`start.bat`**. It opens two windows (backend and frontend) and then your browser at http://localhost:5173. The first start takes a few minutes while it downloads what it needs. Needs Java 17+ and Node.js installed.

Or by hand, in two terminals:

```
cd backend
mvnw.cmd spring-boot:run     # API on http://localhost:8080   (no need to install Maven)
```
```
cd frontend
npm install                  # first time only
npm run dev                  # the page: http://localhost:5173
```

The first start creates `backend/data/` and loads the sample CSVs from `db/` into it. Delete that folder to start fresh.

## The voice assistant (Saathi)

Tap any mic on the Home page, or one of the example sentences, and Saathi opens. **The microphone listens only between your taps**: tap the mic to start, tap it again (or just stop talking, or close the window) to stop. It is never on by itself. Say it (Chrome and Android Chrome have speech built in) or type it. It answers questions and, for changes, shows what it will do and waits for your Yes.

| You say | What happens |
|---|---|
| "Chocolate ka stock kitna hai?" | tells you the stock |
| "Aaj ki sale batao" / "is mahine profit batao" | sales or gross profit for today / this week / this month |
| "Rahul ka udhaar kitna hai?" | what that customer owes |
| "10 Chocolate add karo" | proposes adding stock, then you confirm |
| "Ramesh ne 500 de diye" | proposes recording the payment, then you confirm |
| "Ramesh ko 300 ka udhaar diya" | proposes giving credit, then you confirm |
| "Restocking list bana do", "agle hafte ke liye kya stock mangwana chahiye?", "kaun sa samaan khatam hone wala hai?", "business insights" | lists and summaries |
| "Kaun sa product sabse zyada bika?" / "Kis product pe sabse zyada margin hai?" | top seller / best margin (last 30 days unless you say "aaj" or "hafte") |
| "Kis-kis ko payment reminder bhejna hai?" | customers with overdue udhaar, biggest amount first |
| "Kaunse customers ka udhaar 1 mahine se zyada purana hai?" | unpaid udhaar older than that ("15 din", "2 hafte", "2 mahine" work too) |
| "Aaj ka payment register dikhao" / "is hafte ka payment register" | every payment and udhaar entry of today / the last 7 / 30 days, with totals |

If two customers share a name, Saathi asks which one (showing phone and pending amount). Every change is saved in the credit ledger / stock movements and in `audit_log`, and every sentence is kept in `voice_utterances` (see Conversations). Answers always come from the database, never from guessing.

How it works: `assistant/CommandParser.java` finds the intent with keywords; `AssistantService.java` runs only checked actions. A proposed change is stored on the server and can be confirmed once, so it cannot be changed or repeated from the browser. The parser is deliberately simple; to understand more sentences add keywords there, or replace it with an LLM or the Gnani speech service later (nothing else needs to change).

## What the Inventory page can do

- **Add Item / Edit item / Archive item:** products are checked (no blank names, no duplicates, no negative prices) and every change is written to `audit_log`. An archived product disappears everywhere but its history is kept; a product that is waiting for a delivery cannot be archived.
- **Add stock and Stock history:** stock is never typed over. Each change is a movement, so the history always explains the number.
- **Generate Restocking List:** suggests an amount for each low or out-of-stock item (enough to reach twice its reorder level), with the cost. It can be shared on WhatsApp or copied. **Place order** saves one purchase order per supplier; those items then show as **On Order**. **Mark order received** (the ⋮ menu of an On Order item) adds the ordered amounts to the stock.
- **Tick rows:** "Restocking list for these" makes a list for just the ticked items.
- **View Stock Report** (also the Inventory Value card): stock value by category, and a spreadsheet (CSV) of every item.
- The number cards, the Low Stock Alert and the tabs filter the table; the search and tab live in the address (`/inventory?filter=low&search=bulb`).

## What the Business Advisor page can do

Pick a period (last 7 days, this month, last month, last 3 months, this year); every number is compared with the period just before.

- **Total Sales, Total Purchase Cost, Estimated Profit, Profit Margin:** purchase cost is what the items you *sold* cost you, so profit = sales − purchase cost (before shop expenses).
- **Sales vs Profit Trend** (by day, by week for 3 months, by month for the year) and **Sales by Category**. Hover or tap for the numbers.
- **Top Selling Products** and **Lowest Profit Margin Products** ("View all" opens the full list).
- **Purchase vs Sales Summary:** stock bought (deliveries received) against sales, and **Net Profit** = profit − shop expenses. "View details" lists the period's purchase orders.
- **Expense Breakdown:** where the money went: the cost of the goods sold plus each kind of expense (from the `expenses` table, loaded from `db/expenses.csv`).
- **Key Insights:** written by `advisor/AdvisorService.java` from the numbers (biggest rise and drop, weakest margin, margin change, best category, expenses vs profit).
- **Quick Actions:** a spoken profit report, a margin comparison, **Suggest Price Changes** (products under 15% margin and the price that reaches it; "Set ₹X" saves it like Edit item, into `audit_log`), and **Generate Purchase Plan** (opens the Inventory restocking list).

## What the Customer Handling page can do

Pick a period like on the Business Advisor page; Outstanding and Payments Received are compared with the period before.

- **Total Outstanding, Customers on Udhaar** (with how many are new), **Payments Received, Payments Overdue.** A customer's udhaar is *overdue* when part of it is still unpaid after its due date. Payments clear the oldest udhaar first (`customers/UdhaarBook.java` works this out from the ledger).
- **Customer list** with tabs (All / Pending Payments / Recently Paid = paid in the chosen period) and search by name or phone. **Record Payment**, and the ⋮ menu: **View history** (every entry with the balance after it), **Give udhaar** (with what they took), **Send payment reminder**. **Add Customer** checks the phone number and can take udhaar they already owe.
- **Customer-wise Outstanding** (red = has overdue udhaar, amber = not due yet), **Recent Transactions** (with what was bought on the bill), **Reminders & Insights** (written by `customers/CustomerPageService.java`).
- **Send Reminder** prepares the message for each overdue customer; WhatsApp or SMS opens on the device to send it, and the page notes it in `payment_reminders` so it shows "reminded 2 days ago". The sample phone numbers are masked, so WhatsApp asks you to pick the contact.
- **Ask Saathi about customers** (questions use real names from the shop) and **Add Udhaar/Payment by Voice or Upload.** Uploaded voice notes and call recordings are kept in `backend/data/recordings/` (90 days, up to 20 MB, the same file cannot be uploaded twice, a call recording needs a consent tick). There is no speech-to-text service yet: open a recording, listen, type what was said, and **Send to Saathi**, which proposes the change and waits for your Yes.

## Use it on a phone, and install it as an app

Every page works on a phone: the menu becomes a bar along the bottom, and tables and cards fit the screen. The website is also an installable app (a "PWA"): once opened on a phone it can be added to the home screen, opens full-screen with its own icon, and starts even on a weak connection. Shop data is never stored on the phone by the app itself.

To make the one file that holds everything (website + API), double-click **`build.bat`**, then run
`java -jar backend	argetackend-0.0.1.jar` and open http://localhost:8080.

To use it on shopkeepers' phones, that jar has to run on a computer on the internet with a **https** address (any host that runs Java works: Render, Railway, a small server...). Then on the phone:
- **Android (Chrome):** open the address, tap the ⋮ menu, **Install app** (the Settings page also has an **Install app** button).
- **iPhone (Safari):** open the address, tap Share, **Add to Home Screen**.

Installing needs https. On your own Wi-Fi (`http://192.168.x.x:8080`) the pages work and can be tried on a phone, but the phone will only offer a plain shortcut, not a real install. For a real deployment use PostgreSQL instead of the local H2 file (see below), and add the https address to your Google client ID's allowed origins.

After you change the website, run `build.bat` again to refresh the jar. While developing, keep using `start.bat`.

## Your private settings: email, demo account

Copy `backend/config/local.properties.example` to `backend/config/local.properties` and fill it in (that file is private and is never shared). Restart the backend afterwards.

1. **Emailing the login code.** For Gmail you need an *App password*: Google Account, Security, turn on 2-Step Verification, then App passwords, create one. Put your Gmail address and that password in the file (the example shows where). Any other email service works too; only the host, port, username and password change.
2. **A demo account with data.** Set `dukaan.demo-email` to an email address you can read. Logging in with it opens **Sharma General Store**, which already has products, sales, udhaar, conversations and orders. If that email already made an account earlier, delete the `backend/data` folder and start again.
3. **Continue with Google** (optional): see below.

## Log in

A login code (6 digits) works for **60 seconds**; the page shows a countdown, and a "Send a new code" button once it runs out. After 5 wrong tries the code is cancelled, and a new one can be asked for only after 30 seconds.

**Mobile numbers:** SMS is not set up yet, so after you press **Send OTP** copy the code from the backend window (`OTP for ...`).

**Email:** the code is emailed once you add your mail settings (next section). Until then it is shown in the backend window, and the page tells you so. If an email already has an account, logging in with it opens that account; a new account is only made for a new email (capital letters do not matter).

**For faster testing with a fixed code:** start the backend with:
```
set DUKAAN_FIXED_CODE=111111
mvnw.cmd spring-boot:run
```
Then every OTP request will return `111111` instead of a random code.

| Phone | Owner | Shop |
|---|---|---|
| 9999900001 | Rajesh Sharma | Sharma General Store |
| 9999900002 | Anita Mehra | Bright Stationery House |
| 9999900003 | Vikram Singh | Fresh Basket Mart |
| 9999900004 | Suresh Iyer | Balaji Oil Traders |
| 9999900005 | Kavita Joshi | Daily Needs Corner |
| 9999900006 | Manoj Khanna | Om Household Mart |

Any other 10-digit number starting 6-9 signs up as a new shop (empty dashboard).

**Continue with Google** needs a Google client ID, which only you can create (free, about 5 minutes):
1. Go to https://console.cloud.google.com/apis/credentials and create a project if you don't have one.
2. Configure the OAuth consent screen (type "External", add your own Google account as a test user).
3. Create credentials, then **OAuth client ID**, type **Web application**. Under *Authorised JavaScript origins* add `http://localhost:5173`. Leave the redirect URIs empty.
4. Copy the client ID (it ends in `.apps.googleusercontent.com`) and start the backend with it:
   `set GOOGLE_CLIENT_ID=your-id-here` then `mvnw.cmd spring-boot:run`  (Windows cmd, same window).
   For `start.bat`, set it once with: `setx GOOGLE_CLIENT_ID "your-id-here"` and open a new window.

Until it is set, the Google button shows "Google sign-in is not set up yet". A person who signs in with Google gets their own account (separate from a phone account) and is asked for their shop details the first time. The sample data ends on 18 Oct 2026, so the dashboard treats that day as "today" (`dukaan.today` in `backend/src/main/resources/application.properties`).

## How it is organised

```
backend/src/main/java/com/dukaansaathi/
  auth/        login + sign-up (controller -> service -> repository)
  profile/     /api/me: who is logged in (used by the top bar on every page)
  dashboard/   the Home page data (controller -> service -> repository)
  advisor/     the Business Advisor page: profit & loss for a period, product margins, price suggestions, insights
  inventory/   the Inventory page, and adding / editing / archiving products, stock history and the stock report
  customers/   the Customer Handling page, udhaar (payments, credit, history, reminders) and UdhaarBook (who owes what, what is overdue)
  recordings/  uploading voice notes and call recordings, playing them back, and the text typed for them
  restock/     what to order, purchase orders, and receiving a delivery (which adds the stock)
  settings/    the Settings page: shop profile + preferences (saved in shops, users and shop_settings)
  config/      loads the sample CSVs on first start
backend/src/main/resources/schema.sql   tables + two views (product_stock, customer_balance)
frontend/src/
  App.jsx        the pages and their addresses (/, /inventory, /advisor, /customers, /settings, /help)
  components/    shared by every page: AppLayout (top bar + menu frame), TopBar, Sidebar (laptop), BottomNav (phone),
                 navItems (the list of pages), PageHeader,
                 ActionNeeded (restock list), ui (Card, StatCard, StatusPill, ₹ and date formatting)
  pages/
    login/       landing + login / sign-up
    home/        Home page, one small file per section
    inventory/   Inventory page: Inventory.jsx (the page and its dialogs), ItemsTable.jsx, SideCards.jsx,
                 ItemFormDialogs / StockDialogs / RestockDialog / ReportDialog / RowActionsDialog (one per button)
    settings/    Settings page: Settings.jsx (the form), SettingsCards.jsx (one card per section)
    help/        Help page: Help.jsx, and helpContent.js where all the text is written
    advisor/     Business Advisor page: BusinessAdvisor.jsx (the page), Charts.jsx (trend + ring charts), ProductTables.jsx,
                 SideCards.jsx (Ask Saathi, Key Insights, Quick Actions), AdvisorDialogs.jsx
    customers/   Customer Handling page: Customers.jsx (the page), CustomerTable.jsx, BottomCards.jsx (outstanding bars,
                 recent transactions, reminders & insights), SideCards.jsx (Ask Saathi, voice / upload), CustomerDialogs.jsx
db/              sample data + the full PostgreSQL schema
```

To add a page: make `pages/<name>/<Name>.jsx`, add a `<Route>` in `App.jsx`, add its address to `SpaController.java`, and add a line to `components/navItems.js` (the sidebar and the phone bar both read it). The top bar, sidebar and highlighted box come for free.

Rules the code follows: the shop always comes from the login session, never from the request; every query filters on `shop_id`; money is whole paise; stock and customer balances are always calculated from movements / ledger rows, never stored.

To move from the local H2 file to PostgreSQL: swap the `h2` dependency in `backend/pom.xml` for the PostgreSQL driver and change the three `spring.datasource.*` lines.

## Not built yet

Recording sales and expenses, Hindi in Devanagari letters and the language selector (multilingual), hands-free "always listening" mode, the Gnani speech service (speech-to-text for uploaded recordings), sending reminders automatically from the server, and SMS/email for OTPs. Everything on the Home, Inventory, Business Advisor and Customer Handling pages works except the language selector.
