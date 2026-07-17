-- Create chats table
create table public.chats (
  id uuid default gen_random_uuid() primary key,
  user_id uuid references auth.users(id) on delete cascade not null,
  title text not null,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Enable Row Level Security (RLS)
alter table public.chats enable row level security;

-- Create policy to allow authenticated users to read their own chats
create policy "Allow users to read their own chats" on public.chats
  for select using (auth.uid() = user_id);

-- Create policy to allow authenticated users to insert their own chats
create policy "Allow users to insert their own chats" on public.chats
  for insert with check (auth.uid() = user_id);

-- Create policy to allow authenticated users to update their own chats
create policy "Allow users to update their own chats" on public.chats
  for update using (auth.uid() = user_id);

-- Create policy to allow authenticated users to delete their own chats
create policy "Allow users to delete their own chats" on public.chats
  for delete using (auth.uid() = user_id);
