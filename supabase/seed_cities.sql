-- Broader seed for `cities` (docs/SUPABASE_BOOTSTRAP.md §2), for issue #360 ("make it a broader
-- list"). Run this in the SQL editor same as any other file here — no CLI migration flow, see
-- supabase/README.md's standing note about `db push`.
--
-- Includes the original bootstrap's 24 cities too, so this file is idempotent and safe to run
-- whether or not that seed already has. Tier drives the low-confidence labelling in the UI, same
-- as docs/SUPABASE_BOOTSTRAP.md §20: 1 = the original ten metros, 2 = other large cities and
-- state capitals, 3 = smaller state/UT capitals and other well-known cities.
--
-- One row per city name, project-wide: `cities` carries a unique index on `lower(name)`
-- (docs/SUPABASE_BOOTSTRAP.md §2, "the fairness RPC resolves a city by name"), not just on
-- (name, state) — so a name cannot repeat here even under a different state.

INSERT INTO cities (name, state, tier) VALUES
    -- Tier 1 — the original ten metros.
    ('Mumbai','Maharashtra',1), ('Delhi','Delhi',1), ('Bengaluru','Karnataka',1),
    ('Hyderabad','Telangana',1), ('Chennai','Tamil Nadu',1), ('Kolkata','West Bengal',1),
    ('Pune','Maharashtra',1), ('Ahmedabad','Gujarat',1), ('Jaipur','Rajasthan',1),
    ('Surat','Gujarat',1),

    -- Tier 2 — the original fourteen, plus a broader set of large cities and state capitals.
    ('Lucknow','Uttar Pradesh',2), ('Kanpur','Uttar Pradesh',2), ('Nagpur','Maharashtra',2),
    ('Indore','Madhya Pradesh',2), ('Bhopal','Madhya Pradesh',2), ('Patna','Bihar',2),
    ('Vadodara','Gujarat',2), ('Ludhiana','Punjab',2), ('Agra','Uttar Pradesh',2),
    ('Nashik','Maharashtra',2), ('Coimbatore','Tamil Nadu',2), ('Kochi','Kerala',2),
    ('Chandigarh','Chandigarh',2), ('Visakhapatnam','Andhra Pradesh',2),
    ('Thane','Maharashtra',2), ('Navi Mumbai','Maharashtra',2), ('Aurangabad','Maharashtra',2),
    ('Solapur','Maharashtra',2), ('Rajkot','Gujarat',2), ('Bhavnagar','Gujarat',2),
    ('Jamnagar','Gujarat',2), ('Faridabad','Haryana',2), ('Gurugram','Haryana',2),
    ('Panipat','Haryana',2), ('Karnal','Haryana',2), ('Rohtak','Haryana',2),
    ('Hisar','Haryana',2), ('Ambala','Haryana',2), ('Ghaziabad','Uttar Pradesh',2),
    ('Noida','Uttar Pradesh',2), ('Meerut','Uttar Pradesh',2), ('Varanasi','Uttar Pradesh',2),
    ('Amritsar','Punjab',2), ('Jalandhar','Punjab',2), ('Guwahati','Assam',2),
    ('Bhubaneswar','Odisha',2), ('Cuttack','Odisha',2), ('Ranchi','Jharkhand',2),
    ('Jamshedpur','Jharkhand',2), ('Dhanbad','Jharkhand',2), ('Raipur','Chhattisgarh',2),
    ('Bhilai','Chhattisgarh',2), ('Dehradun','Uttarakhand',2), ('Jodhpur','Rajasthan',2),
    ('Kota','Rajasthan',2), ('Udaipur','Rajasthan',2), ('Bikaner','Rajasthan',2),
    ('Ajmer','Rajasthan',2), ('Madurai','Tamil Nadu',2), ('Tiruchirappalli','Tamil Nadu',2),
    ('Salem','Tamil Nadu',2), ('Vellore','Tamil Nadu',2), ('Mysuru','Karnataka',2),
    ('Hubballi','Karnataka',2), ('Mangaluru','Karnataka',2), ('Belagavi','Karnataka',2),
    ('Gwalior','Madhya Pradesh',2), ('Jabalpur','Madhya Pradesh',2), ('Ujjain','Madhya Pradesh',2),
    ('Vijayawada','Andhra Pradesh',2), ('Guntur','Andhra Pradesh',2),
    ('Nellore','Andhra Pradesh',2), ('Tirupati','Andhra Pradesh',2),
    ('Siliguri','West Bengal',2), ('Durgapur','West Bengal',2), ('Asansol','West Bengal',2),
    ('Warangal','Telangana',2), ('Nizamabad','Telangana',2),
    ('Thiruvananthapuram','Kerala',2), ('Kozhikode','Kerala',2), ('Thrissur','Kerala',2),

    -- Tier 3 — smaller state/UT capitals and other well-known cities.
    ('Srinagar','Jammu and Kashmir',3), ('Jammu','Jammu and Kashmir',3),
    ('Shimla','Himachal Pradesh',3), ('Panaji','Goa',3), ('Imphal','Manipur',3),
    ('Shillong','Meghalaya',3), ('Aizawl','Mizoram',3), ('Kohima','Nagaland',3),
    ('Itanagar','Arunachal Pradesh',3), ('Agartala','Tripura',3), ('Gangtok','Sikkim',3),
    ('Puducherry','Puducherry',3), ('Port Blair','Andaman and Nicobar Islands',3),
    ('Silchar','Assam',3), ('Dibrugarh','Assam',3), ('Patiala','Punjab',3),
    ('Bathinda','Punjab',3), ('Bhilwara','Rajasthan',3), ('Sikar','Rajasthan',3),
    ('Alwar','Rajasthan',3), ('Bilaspur','Chhattisgarh',3), ('Rourkela','Odisha',3),
    ('Bokaro','Jharkhand',3), ('Muzaffarpur','Bihar',3), ('Gaya','Bihar',3),
    ('Bhagalpur','Bihar',3), ('Haridwar','Uttarakhand',3), ('Rishikesh','Uttarakhand',3),
    ('Kollam','Kerala',3), ('Kannur','Kerala',3), ('Alappuzha','Kerala',3),
    ('Tirunelveli','Tamil Nadu',3), ('Erode','Tamil Nadu',3), ('Thoothukudi','Tamil Nadu',3),
    ('Kakinada','Andhra Pradesh',3), ('Rajahmundry','Andhra Pradesh',3),
    ('Anantapur','Andhra Pradesh',3), ('Karimnagar','Telangana',3),
    ('Davanagere','Karnataka',3), ('Ballari','Karnataka',3), ('Shivamogga','Karnataka',3),
    ('Sagar','Madhya Pradesh',3), ('Satna','Madhya Pradesh',3), ('Rewa','Madhya Pradesh',3),
    ('Ratlam','Madhya Pradesh',3), ('Muzaffarnagar','Uttar Pradesh',3),
    ('Saharanpur','Uttar Pradesh',3), ('Gorakhpur','Uttar Pradesh',3),
    ('Firozabad','Uttar Pradesh',3), ('Jhansi','Uttar Pradesh',3), ('Mathura','Uttar Pradesh',3),
    ('Moradabad','Uttar Pradesh',3), ('Bareilly','Uttar Pradesh',3),
    ('Aligarh','Uttar Pradesh',3), ('Prayagraj','Uttar Pradesh',3)
ON CONFLICT (name, state) DO NOTHING;
