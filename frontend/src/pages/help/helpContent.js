// Everything written on the Help page. Edit the text here.

export const STEPS = [
  ['Log in', 'Use your mobile number, your email or Google. A new shop is set up in about a minute.'],
  ['See your shop at a glance', 'Home shows your sales, pending udhaar, low stock and recent conversations.'],
  ['Keep your stock in check', 'Inventory shows what is low, what is out of stock and what is already on order.'],
  ['Talk to Saathi', 'Tap the mic and say what you need (tap it again to stop), or type it. Saathi answers, and asks you before it changes anything.'],
]

export const VOICE_GROUPS = [
  ['Stock', ['Maggi ka stock kitna hai?', '10 packet Amul Milk add karo', 'Kaun sa samaan khatam hone wala hai?', 'Restocking list bana do']],
  ['Sales and profit', ['Aaj ki total sale batao', 'Is mahine profit kam kyun hua?', 'Mere kharche kahan badh rahe hain?']],
  ['Udhaar', ['Ramesh ka udhaar kitna hai?', 'Ramesh ne ₹500 de diye', 'Ramesh ko ₹300 ka samaan udhaar diya']],
]

export const FAQ = [
  ['How is my stock worked out?',
    'Every sale, purchase, return and stock correction is saved as a movement. Your stock is the opening stock plus all of those movements, so you can always see how a number came about.'],
  ['What do Low Stock and Out of Stock mean?',
    'A product is Low Stock when what is left is at or below its reorder level. It is Out of Stock when none is left. Both appear in the Action Needed and Low Stock Alert lists.'],
  ['How is pending udhaar calculated?',
    'For each customer: credit you gave, minus payments they made, plus or minus any corrections. Only entries you have confirmed are counted, so nothing is added by a guess.'],
  ['What is gross profit?',
    'Sales minus what the goods cost you. It is before your shop expenses such as rent and wages, so it is higher than your final profit.'],
  ['Why does Today show ₹0?',
    'No sales have been recorded for that day yet. Choose Last 7 days or Last 30 days at the top of Home to see more.'],
  ['Can other shops see my data?',
    'No. Everything you see belongs to your shop only, and the server checks this on every request.'],
  ['How do I put Dukaan Saathi on my phone?',
    'On Android, open the website in Chrome, tap the ⋮ menu and choose Install app. On iPhone, open it in Safari, tap Share and choose Add to Home Screen. It then opens full-screen from your home screen, like any other app. The Settings page also has an Install app button.'],
  ['How do I reorder stock that is running low?',
    'Open Inventory and tap Generate Restocking List. Saathi suggests how much to order for each low item. You can change the numbers, share the list on WhatsApp with your supplier, and tap Place order to save it. The items then show as On Order. When the delivery arrives, tap the three dots on the item and choose Mark order received: your stock goes up by itself.'],
  ['How do I add a new item or fix a wrong price?',
    'On Inventory, tap Add Item and fill in the form. To change an existing item, tap the three dots at the end of its row and choose Edit item. Use Add stock when more arrives, and Archive item to hide something you no longer sell (its history is kept).'],
  ['Is the microphone always on?',
    'No. Saathi listens only after you tap the mic, and stops when you finish speaking or tap the mic again. Closing the Saathi window switches the microphone off.'],
  ['Does the voice assistant work?',
    'Yes. Tap the mic (Chrome works best) and speak, or type the same sentence in the box. Saathi understands English and Hindi written in English letters, like "Ramesh ne 500 de diye". Before it adds stock or records a payment, it shows you exactly what it will do and waits for your Yes. Hindi in Devanagari letters and other languages will come later.'],
]
