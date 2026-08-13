# Sundial 多端同步：Supabase 初始化

## 前置条件

- 一个 Supabase 项目（https://supabase.com 免费档即可）
- 应用内已编译的新版本（含同步后端）

## 步骤

1. 打开 Supabase Dashboard → 你的项目 → SQL Editor。
2. 粘贴并执行以下脚本（可重复执行，幂等）：

```sql
create table if not exists public.reminder_list (
  id bigint primary key,
  name text not null,
  color_key text not null,
  position bigint not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  updated_by text not null default ''
);

create table if not exists public.todo (
  id bigint primary key,
  list_id bigint not null,
  title text not null,
  note text not null default '',
  due_date bigint,
  is_completed boolean not null default false,
  completed_at bigint,
  is_trashed boolean not null default false,
  trashed_at bigint,
  parent_id bigint,
  sort_position double precision not null default 0,
  flag boolean not null default false,
  recurrence_frequency text,
  recurrence_interval bigint,
  created_at bigint not null,
  updated_at bigint not null,
  updated_by text not null default ''
);

alter table public.todo add column if not exists recurrence_frequency text;
alter table public.todo add column if not exists recurrence_interval bigint;

create index if not exists idx_todo_list_trash on public.todo(list_id, is_trashed);
create index if not exists idx_todo_trash_due on public.todo(is_trashed, due_date);
create index if not exists idx_todo_parent on public.todo(parent_id);

alter table public.reminder_list enable row level security;
alter table public.todo enable row level security;

-- 无登录模型：anon 放开（个人使用；任何持有 anon key 者均可读写）
create policy "anon all reminder_list" on public.reminder_list for all to anon using (true) with check (true);
create policy "anon all todo" on public.todo for all to anon using (true) with check (true);

-- 远端 DELETE 事件需要完整旧行（含 updated_at）做 LWW 守卫；默认 PK-only 会丢删除
alter table public.todo replica identity full;
alter table public.reminder_list replica identity full;

alter publication supabase_realtime add table public.reminder_list;
alter publication supabase_realtime add table public.todo;
```

> 注意：如果你在本脚本修改前已建过表（旧版含 `references public.reminder_list(id)` 外键），需要先删除该外键，否则删除列表时会触发外键约束错误并使同步卡死：
>
> ```sql
> alter table public.todo drop constraint todo_list_id_fkey;
> ```
>
> 约束名大概率是 `todo_list_id_fkey`；若执行报错，请以错误信息或 `\d public.todo` 显示的约束名为准。

3. 打开 Dashboard → Realtime，确认 `todo` 与 `reminder_list` 已在 publication 中（若 SQL Editor 已执行上述脚本，默认已加入）。
4. 打开应用 → 侧边栏 → 设置 → 选择「Supabase 云端」→ 填入项目的 URL（Project Settings → API → Project URL）与 anon key（Project Settings → API → anon public key）→ 保存。
5. 应用开始推送本地 outbox 并订阅远端变化；设置页显示连接与待同步状态。

## 安全边界（重要）

- anon key 是**公开**的（客户端内嵌），本方案 RLS 对 anon 完全放开——**任何拿到 anon key 的人都能读写你的数据**。
- 仅适合个人自用项目；若需多人或多租户，应换用真实登录（Auth）+ 面向用户的 RLS 策略，或迁移到 Sundial-Server（计划中）。
- 无墓碑机制：若设备 A 离线期间的旧变更晚于设备 B 的删除到达，可能把已删除的行重新写回（LWW 语义下的已知限制）。
- 无外键约束：`todo.list_id` 不引用 `reminder_list(id)`，已删除列表的孤儿待办行可能残留在服务器（应用内不可见），属 LWW 语义下的已知限制。

## 同步语义（摘要）

- 行级 LWW：`updated_at` 大者胜；等值平局时后到者胜。
- 本地所有写入先落 outbox，成功后清理；断网时 outbox 保留，恢复后自动重推（幂等）。
- 客户端过滤自身设备的回声（`updated_by` 对比）。
