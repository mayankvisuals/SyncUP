SyncUP is a feature-rich, modern social messaging application built using Jetpack Compose, Kotlin, and Firebase. This app is an excellent combination of real-time chat (like WhatsApp) and social media features (like Instagram).

 *Key Features
💬 Real-time Chat
Personal & Group Chats: Users can chat 1-on-1 or in group channels.

Media Sharing: Facility to send photos and videos.

Advanced Messaging:

Message Replies: Reply to any message.

Reactions: React to messages with emojis.

Typing Indicators: See who is typing in real-time.

Seen Status: Track message receipts with the "Seen by" feature.

Edit & Unsend: Edit or unsend sent messages.

Chat Management: Mute or hide chats.

👥 Social Networking
User Profiles: Each user has a custom profile (photo, bio, username).

Follow System: Users can follow/unfollow each other.

User Discovery: Search for other users by username.

Notifications: Real-time alerts for new followers and other activities.

📸 Ephemeral Stories
Create Stories: Create 24-hour stories from photos or videos in the gallery.

Add Music: Add music to your stories by searching on YouTube.

Interactive Viewer: A full-screen, Instagram-like story viewer.

Privacy: See who has viewed your story.

Story Management: Delete your own stories.

🛠️ Tech Stack & Architecture
UI: 100% built with Jetpack Compose.

Language: Kotlin

Architecture: MVVM (Model-View-ViewModel)

Dependency Injection: Hilt

Backend & Real-time Features:

Firebase Realtime Database: For messages, user data, and real-time updates.

Firebase Authentication: For secure user login and signup.

Firebase Cloud Messaging (FCM): For push notifications.

Media Storage: Supabase Storage (Profile photos, chat media, etc.)

Async Operations: Kotlin Coroutines & Flow

Navigation: Jetpack Navigation Compose

Image Loading: Coil

API Calls: Ktor (YouTube API) & Volley (FCM)
