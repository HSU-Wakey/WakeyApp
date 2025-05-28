# 📱 Wakey : On-device AI 기반 인생발자취 및 스마트앨범 서비스

**2025 캡스톤디자인 졸업 프로젝트**  

Qualcomm AI hub를 기반으로 Galaxy S25의 On-device AI 모델을 활용하여  
인생발자취 기록과 스마트앨범 기능을 제공하는 AI 서비스입니다.

---

## 📌 프로젝트 소개

**Wakey**는 On-device AI 기반으로 사진을 자동 태그하고, 자연어 기반 검색을 지원하며,  
인생 타임라인 및 스토리 생성을 통해 사용자의 추억을 효과적으로 관리하는 서비스입니다.

- **On-device AI** : Snapdragon 8 Gen 3 기반 Galaxy S25에 AI 모델을 탑재하여  
  서버 연동 없이 단말 내에서 AI 기능을 구현하는 솔루션.

---

## 🛠️ 주요 기능

### 📍 SmartTag  
Yolov8 + MobileNet v3 기반 이미지 해시태깅

- **Yolov8 detection** : 이미지 내 객체 탐지 및 해시태그 생성  
- **MobileNet v3** : 탐지된 객체 중심의 영역 crop하여 객체 분류  
- On-Device 연산을 통해 인터넷 없이 빠른 태그 생성  

예시 : `#Motorcycle`, `#PhotoSpot`, `#Shop`

---

### 🔍 SmartSearch  
자연어 기반 이미지 검색 기능

- **OpenAI CLIP**  
  - 이미지 ↔ 텍스트 임베딩 매칭  
  - 자연어로 사진 검색 가능  
  - 512차원 벡터로 텍스트 및 이미지 임베딩 변환  
- On-Device 최적화 모델을 활용하여 빠른 검색 지원  

예시 : `바닷가에서 찍은 사진 찾아줘`

---

### 🗺️ SmartStory & Timeline  
인생발자취 타임라인 및 이미지 스토리 생성

- **ESRGAN (초해상화 모델)** : 저화질 이미지 업스케일링 지원  
- 이미지 촬영 장소, 시간, 태그 기반으로 자동 타임라인 구성  
- 선택 사진으로 스토리 자동 생성 및 지도에 동선 표시

---

### 📂 SmartAlbum  
해외, 국내, 상세지역 별로 앨범 자동 분류 기능  
위치 기반 메타데이터 활용하여 자동 정리  

---

## 📷 사용 AI 모델

- **Yolov8-detection** : 이미지 객체 탐지  
- **ESRGAN** : 이미지 초해상화  
- **MobileNet v3** : 경량 객체 분류  
- **OpenAI CLIP** : 이미지-텍스트 임베딩

---

## 📱 개발 환경

- **디바이스** : Galaxy S25 (Snapdragon 8 Gen 3)  
- **On-device AI** : Qualcomm AI hub 활용  
- **Android Native App**  
- **AI 모델 최적화 및 TensorFlow Lite / Qualcomm SDK 연동**

---

## 📖 팀 정보

- **팀명** : 12팀  
- **프로젝트명** : Wakey  
- **소속** : 2025 캡스톤디자인 퀄컴

---

## 📌 QR 코드


👉 앱 시연 및 설명 영상 : https://youtu.be/GG55KUtXJIg

---

