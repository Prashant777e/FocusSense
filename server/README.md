# FocusSense Backend & Supabase Database Deployment Guide

This backend connects Parent and Child devices in real time and acts as the intelligent router/load-balancer to your dedicated open-source DeepSeek LLM server.

---

## 1. How to Build the Database in PostgreSQL (Supabase)

### Step 1: Create your free Supabase Project
1. Go to [https://supabase.com](https://supabase.com) and log in or create a free account.
2. Click **New Project**.
3. Choose a project name (e.g. `focussense-db`), set a strong database password, and select your preferred region.

### Step 2: Run the Schema
1. In your Supabase Dashboard, click on the **SQL Editor** tab (terminal icon `>_` on the left sidebar).
2. Click **New Query**.
3. Open the file `server/schema.sql` from this repository, copy its entire contents, paste it into the editor, and click **Run** (green button).
4. You will see `Success. No rows returned`.
5. Click **Table Editor** on the left menu: you will now see all 6 tables created:
   - `family_groups`
   - `users`
   - `devices`
   - `activity_logs`
   - `schedule_rules`
   - `location_history`

### Step 3: Copy your Database URL
1. Go to **Project Settings** (gear icon) -> **Database**.
2. Scroll down to **Connection string**.
3. Select the **URI** tab and choose **Session pooler** or **Direct connection**.
4. It looks like:
   ```
   postgresql://postgres.[YOUR-PROJECT-REF]:[YOUR-PASSWORD]@aws-0-[REGION].pooler.supabase.com:6543/postgres
   ```
   *(Replace `[YOUR-PASSWORD]` with the database password you created in Step 1).*

---

## 2. Deploying the Backend on Render (Free Web Service)

### Step 1: Push this code to GitHub
Push your project repository to GitHub (or use the Render Git connection).

### Step 2: Create a Web Service on Render
1. Go to [https://render.com](https://render.com) and sign in.
2. Click **New +** -> **Web Service**.
3. Select your GitHub repository.
4. Configure the service:
   - **Name:** `focussense-api`
   - **Root Directory:** `server`
   - **Runtime:** `Python 3`
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `uvicorn main:app --host 0.0.0.0 --port $PORT`
   - **Instance Type:** `Free`

### Step 3: Set Environment Variables in Render
In the **Environment** tab on Render, add these variables:
- `DATABASE_URL`: Your Supabase connection string from Section 1 above.
- `DEEPSEEK_SERVER_URL` *(Optional)*: The URL of your self-hosted DeepSeek inference server (e.g., `http://your-gpu-ip:8000/v1/chat/completions` or `http://your-gpu-ip:11434/api/chat`).
  - *Note: If left blank, FocusSense automatically uses its built-in rule heuristic engine.*
- `DEEPSEEK_API_KEY` *(Optional)*: API key if your DeepSeek server requires authentication.
- `DEEPSEEK_MODEL` *(Optional)*: e.g. `deepseek-chat` or `deepseek-r1-distill-qwen-7b`.

Click **Deploy Web Service**. Once deployed, Render will provide your public URL:
`https://focussense-api.onrender.com`

---

## 3. Connecting the Android App

In the FocusSense Android app:
- Open the app on either Parent or Child phone.
- Tap **Advanced: Configure Server URL**.
- Enter your live Render URL (e.g. `https://focussense-api.onrender.com`).
- Tap **Test Server Connection** to verify live communication with Supabase!
