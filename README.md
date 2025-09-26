# 📱 SyncUP

**SyncUP** is a feature-rich, modern social messaging application built using **Jetpack Compose**, **Kotlin**, and **Firebase**.  
It combines **real-time chat (like WhatsApp)** with **social media features (like Instagram)** to deliver a seamless, all-in-one experience.

---

## ✨ Key Features

### 💬 Real-time Chat
- **Personal & Group Chats**: 1-on-1 or group messaging.
- **Media Sharing**: Send photos and videos.
- **Advanced Messaging**:
  - Reply to specific messages.
  - React with emojis.
  - Real-time typing indicators.
  - “Seen by” message receipts.
  - Edit or unsend messages.
  - Mute or hide chats.

### 👥 Social Networking
- **User Profiles**: Custom photo, bio, and username.
- **Follow System**: Follow/unfollow other users.
- **User Discovery**: Search by username.
- **Notifications**: Real-time alerts for followers & activities.

### 📸 Ephemeral Stories
- **Create Stories**: Share 24-hour photo/video stories.
- **Add Music**: Attach music via YouTube search.
- **Interactive Viewer**: Instagram-like full-screen stories.
- **Privacy & Insights**: See who viewed your stories.
- **Story Management**: Delete your own stories.

---

## 🛠 Tech Stack & Architecture

### Frontend
- **UI**: 100% Jetpack Compose
- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Navigation**: Jetpack Navigation Compose
- **Image Loading**: Coil

### Backend & Services
- **Firebase Realtime Database**: Messages, user data, real-time sync.
- **Firebase Authentication**: Secure signup & login.
- **Firebase Cloud Messaging (FCM)**: Push notifications.
- **Supabase Storage**: Profile photos & media storage.
- **Async Operations**: Kotlin Coroutines & Flow
- **API Calls**: 
  - **Ktor** → YouTube API (music search)
  - **Volley** → FCM handling

---
