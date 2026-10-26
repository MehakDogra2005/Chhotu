// Everything written on the Help page. Edit the text here.

export const STEPS = [
  ['Log in', 'Use your mobile number, your email or Google. A new shop is set up in about a minute.'],
  ['See your shop at a glance', 'Home shows your sales, pending udhaar, low stock and recent conversations.'],
  ['Keep your stock in check', 'Inventory shows what is low, what is out of stock and what is already on order.'],
  ['Talk to Saathi', 'Say what you need in Hindi, Hinglish or English. Voice is coming soon.'],
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
  ['When will voice work?',
    'The voice assistant is still being built. The mic and the voice buttons show where it will be. Your voice choices in Settings are saved and will be used once it is ready.'],
]
