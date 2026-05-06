insert into public.job_template (path, params_template) values ('vsc/git', '{"url": "", "branch": "", "login": "", "password": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('vsc/mercurial', '{"url": "", "branch": "", "login": "", "password": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('build/maven', '{"version": "", "args": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('build/gradle', '{"version": "", "args": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('build/javac', '{"version": "", "args": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('build/gcc', '{"version": "", "args": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('fuzzing', '{"function": ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('deploy/windows/cmd', '{"ssh": {"ip": "", "user": "", "password": ""}, "script":  ""}') on conflict do nothing;
insert into public.job_template (path, params_template) values ('deploy/linux/bash', '{"ssh": {"ip": "", "user": "", "password": ""}, "script":  ""}') on conflict do nothing;
