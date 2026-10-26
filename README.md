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

## Log in

OTPs are not sent as SMS yet: after you press **Send OTP**, copy the 6-digit code from the backend terminal (`OTP for ...`).

| Phone | Owner | Shop |
|---|---|---|
| 9999900001 | Rajesh Sharma | Sharma General Store |
| 9999900002 | Anita Mehra | Bright Stationery House |
| 9999900003 | Vikram Singh | Fresh Basket Mart |
| 9999900004 | Suresh Iyer | Balaji Oil Traders |
| 9999900005 | Kavita Joshi | Daily Needs Corner |
| 9999900006 | Manoj Khanna | Om Household Mart |

Any other 10-digit number starting 6-9 signs up as a new shop (empty dashboard).

**Continue with Email** works the same way: enter any email address, press Send OTP, and copy the code from the backend terminal. Nothing is emailed yet.

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
  inventory/   the Inventory page data + the stock queries Home and the top bar also use
  settings/    the Settings page: shop profile + preferences (saved in shops, users and shop_settings)
  config/      loads the sample CSVs on first start
backend/src/main/resources/schema.sql   tables + two views (product_stock, customer_balance)
frontend/src/
  App.jsx        the pages and their addresses (/, /inventory, /advisor, /customers, /settings, /help)
  components/    shared by every page: AppLayout (top bar + sidebar frame), TopBar, Sidebar, PageHeader,
                 ActionNeeded (restock list), ui (Card, StatCard, StatusPill, ₹ and date formatting)
  pages/
    login/       landing + login / sign-up
    home/        Home page, one small file per section
    inventory/   Inventory page: Inventory.jsx, ItemsTable.jsx, SideCards.jsx
    settings/    Settings page: Settings.jsx (the form), SettingsCards.jsx (one card per section)
    help/        Help page: Help.jsx, and helpContent.js where all the text is written
    advisor/ customers/   placeholders, to be built
db/              sample data + the full PostgreSQL schema
```

To add a page: make `pages/<name>/<Name>.jsx`, add a `<Route>` in `App.jsx`, and add a line to `PAGES` in `components/Sidebar.jsx`. The top bar, sidebar and highlighted box come for free.

Rules the code follows: the shop always comes from the login session, never from the request; every query filters on `shop_id`; money is whole paise; stock and customer balances are always calculated from movements / ledger rows, never stored.

To move from the local H2 file to PostgreSQL: swap the `h2` dependency in `backend/pom.xml` for the PostgreSQL driver and change the three `spring.datasource.*` lines.

## Not built yet

The voice assistant (the mic, the voice buttons), the other pages (Business Advisor, Customer Handling), adding / editing products, "Record Payment", and SMS for OTPs. Their buttons are in the design but do nothing yet. Voice and payment-reminder choices in Settings are saved but only take effect once those features exist (the low-stock alert switch already controls the bell).
