-- A fact bank that is not empty on a fresh project.
--
-- `generate` picks the least recently used row and asks the model to write copy around it. An
-- empty bank means it answers "content_bank empty" and the whole pipeline has nothing to do —
-- which is what development did, while production worked because somebody had run
-- social-automation/supabase/seed_content_bank.sql by hand.
--
-- **Only when the bank is empty.** On production this is a no-op, so it cannot duplicate rows
-- there — the table has no unique key on `fact`, and re-running a plain insert would.
--
-- Four rows, not the whole seed file. This is enough for the pipeline to run and for somebody
-- to see it run; the rest is content, and the panel's Fact bank tab is where content belongs.
--
-- Idempotent, like everything in docs/SUPABASE_BOOTSTRAP.md — safe to re-run.

insert into social.content_bank (category, fact, stats, cta)
select category, fact, stats::jsonb, cta from (values
('fine', 'PUC expire hone pe challan ₹10,000 tak ja sakta hai. Zyada tar logo ko apni expiry date yaad nahi hoti.',
 '[{"label":"CHALLAN","value":"₹10,000"},{"label":"CHECK TIME","value":"10 sec"}]', 'Apni date check karo — Odo'),
('fine', 'Insurance lapse = ₹2,000 fine, aur accident me claim ZERO. Renewal date phone me honi chahiye, dimaag me nahi.',
 '[{"label":"FINE","value":"₹2,000"},{"label":"CLAIM","value":"₹0"}]', 'Reminder lagao — Odo'),
('fine', 'RC, insurance, PUC — ye 3 documents gaadi me na hon to challan pakka hai. Digital copy 2 tap me dikha sakte ho.',
 '[{"label":"DOCUMENTS","value":"3"},{"label":"TAPS","value":"2"}]', 'Vault me rakho — Odo'),
('money_saved', 'Service wale "engine flush" jaise extra items likh dete hain jinki zaroorat nahi hoti. Apna service record ho to sawaal puch sakte ho.',
 '[{"label":"TYPICAL ADD-ON","value":"₹4,000"}]', 'Har bill track karo — Odo')
) as seed (category, fact, stats, cta)
where not exists (select 1 from social.content_bank);
