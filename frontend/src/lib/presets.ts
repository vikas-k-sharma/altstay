export interface HostelPreset {
  id: string;
  name: string;
  location: string;
  propertyName: string;
  knowledgeBase: string;
  suggestedQuestions: string[];
}

export const HOSTEL_PRESETS: HostelPreset[] = [
  {
    id: 'zostel-goa',
    name: 'Zostel Plus Goa',
    location: 'Mandrem, Goa',
    propertyName: 'Zostel Plus Goa',
    knowledgeBase: `## Property Overview
Zostel Plus Goa is a boutique beachfront surf & backpacker hostel in Mandrem, North Goa.

## Check-in & Check-out
- Check-in time: from 2:00 PM to 11:00 PM. Early check-in subject to bed availability.
- Check-out time: strictly by 11:00 AM. Late check-out incurs ₹300/hour fee.
- Government photo ID required at check-in (Aadhaar/Passport/Driving License). PAN card is not accepted.

## Rooms & Pricing
- 6-bed Mixed AC Dorm: ₹650 per night per bed.
- 4-bed Female-only AC Dorm: ₹750 per night per bed.
- Deluxe Private Sea-View Room: ₹2,400 per night (double occupancy).

## Amenities & Services
- High-speed WiFi (150 Mbps) across all dorms and the co-working lounge.
- Daily surf lessons: 7:30 AM and 4:30 PM (₹1,200 per session with board rental).
- In-house cafe 'Salt & Sand': breakfast 8 AM - 11 AM, open till 11 PM.
- Scooters available on rent: ₹400/day.

## Rules & Policies
- Strictly NO pets allowed on property.
- Quiet hours: 11:00 PM to 8:00 AM in dorm corridors.
- Outside alcohol is permitted only in the outdoor cafe patio, strictly not in dorm rooms.
- Age restriction: guests must be 18 years or older.`,
    suggestedQuestions: [
      'What time is check-in and check-out?',
      'How much does a mixed dorm bed cost?',
      'Do you offer surfing lessons?',
      'Can I bring my pet dog?',
    ],
  },
  {
    id: 'moustache-rishikesh',
    name: 'Moustache Rishikesh',
    location: 'Tapovan, Rishikesh',
    propertyName: 'Moustache Riverside Rishikesh',
    knowledgeBase: `## Property Overview
Moustache Rishikesh is a riverside retreat nestled in Tapovan with Ganges view and rooftop yoga shala.

## Check-in & Check-out
- Check-in time: from 1:00 PM.
- Check-out time: by 10:30 AM.
- 24/7 front desk assistance available.

## Accommodation & Rates
- 8-bed Riverview Dorm: ₹550 per bed per night.
- 4-bed Deluxe Dorm: ₹700 per bed per night.
- Private Ganga-View Cottage: ₹3,200 per night.

## Activities & Amenities
- Complimentary morning yoga class daily from 7:00 AM to 8:15 AM on the rooftop.
- Ganga river rafting desk: 16 km Shivpuri stretch at ₹850/person.
- Rooftop vegetarian organic cafe serving Ayurvedic thalis and fresh juices.
- Lockers provided in all dorms (bring your own padlock or buy for ₹50).

## House Policies
- 100% vegetarian property. Non-vegetarian food is strictly prohibited.
- Alcohol and smoking are prohibited anywhere on property premises.
- Laundry service: ₹150 per 5 kg load (24h turnaround).`,
    suggestedQuestions: [
      'What time is the morning yoga class?',
      'Is non-vegetarian food allowed?',
      'Do you arrange river rafting trips?',
      'Do I need to bring my own padlock for lockers?',
    ],
  },
  {
    id: 'altstay-jaipur',
    name: 'AltStay Heritage Jaipur',
    location: 'Old City, Jaipur',
    propertyName: 'AltStay Heritage Haveli Jaipur',
    knowledgeBase: `## Property Overview
AltStay Heritage Haveli is a restored 180-year-old Rajasthani haveli situated 10 mins from Hawa Mahal.

## Check-in & Check-out
- Check-in: 2:00 PM onwards.
- Check-out: 11:00 AM.
- Luggage storage is free of charge before check-in and after check-out.

## Rooms & Tariffs
- 6-bed Royal Jharokha Dorm: ₹600 per bed per night.
- Heritage Courtyard Suite: ₹2,800 per night.

## Experiences & Amenities
- Daily complimentary Rooftop Sunset Masala Chai & storytelling at 5:30 PM.
- Jaipur heritage walking tour: 8:00 AM every Friday and Sunday (₹300/person).
- High-speed fiber WiFi in common courtyard and terrace.
- Traditional homecooked Rajasthani dinner buffet: ₹350 per person (served 8 PM - 10 PM).

## Policies
- Outside guests not allowed inside dorm rooms past 8:00 PM.
- Alcohol consumption restricted to the private terrace area only.
- Pets: small cats/dogs allowed ONLY in private courtyard suites (not in dorms).`,
    suggestedQuestions: [
      'When is the sunset chai served?',
      'Can I store my bags after check-out?',
      'Are pets allowed in the dorms?',
      'How far is the property from Hawa Mahal?',
    ],
  },
];

export const DEFAULT_PRESET = HOSTEL_PRESETS[0];
