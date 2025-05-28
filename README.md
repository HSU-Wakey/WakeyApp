# 📱 Wakey : On-device AI 기반 인생발자취 및 스마트앨범 서비스 with Qualcomm

👉 **앱 시연 영상** : https://youtu.be/GG55KUtXJIg  

---

## 📸 프로젝트 소개

**Wakey**는 On-device AI 기반으로 사진을 자동 태그하고,  
자연어 기반 검색과 인생 타임라인 생성을 통해  
사용자의 추억을 쉽고 체계적으로 관리할 수 있는 스마트 앨범 서비스입니다.

Galaxy S25 (Snapdragon 8 Gen 3)와 Qualcomm AI Hub를 기반으로  
모든 AI 처리를 On-Device에서 수행하여 빠르고 안전한 기능을 제공합니다.

![phone](images/phone.png)

---

## 🖥️ 시스템 구조도

서비스의 전체 구성 및 On-Device AI 연산 흐름은 아래와 같습니다.

![structure](images/structure.png)

---

## 📌 주요 기능

![pamphlet](images/pamphlet.png)


### 📍 SmartTag  
**Yolov8 + MobileNet v3 기반 이미지 해시태깅 기능**

- **Yolov8** : 이미지 내 객체 탐지 및 해시태그 생성  
- **MobileNet v3** : 탐지된 객체를 영역 crop하여 세부 분류  
- On-Device AI 연산으로 인터넷 없이 빠른 태그 제공  

> 예: `#Motorcycle`, `#PhotoSpot`, `#Shop`, `#Umbrella`, `#Soup`

---

### 🔍 SmartSearch  
**자연어 기반 이미지 검색 지원**

- **OpenAI CLIP 모델 활용**  
  - 이미지와 텍스트를 512차원 벡터로 변환하여  
    이미지 ↔ 텍스트 임베딩 매칭 수행
  - 자연어로 입력한 문장으로 사진 검색 가능  
  - 예: `"a beautiful sunset on the beach, yummy food with beer, pork with grilling pan"`  

On-Device AI 기반으로 빠르고 정확한 검색 제공  

---

### 🗺️ SmartStory & Timeline  
**인생발자취 타임라인 및 스토리 생성**

- 촬영 장소, 시간, 태그 기반으로 자동 타임라인 구성  
- 선택 사진으로 스토리 생성, 지도 기반 동선 시각화  
- **ESRGAN 모델** 활용해 저화질 이미지 업스케일링 지원  

---

### 📂 SmartAlbum  
**위치 기반 자동 앨범 분류**

- 국내, 해외, 세부 지역별 앨범 자동 정리  
- 위치 메타데이터 활용한 앨범 구성  

---

## 🧠 사용 AI 모델

- **Yolov8** : 이미지 객체 탐지 및 바운딩 박스 crop  
- **MobileNet v3** : 경량 객체 분류  
- **OpenAI CLIP** : 이미지 ↔ 자연어 임베딩 기반 자연어 검색  
- **ESRGAN** : 이미지 초해상화 (업스케일링)

---

## 📱 개발 환경

- **디바이스** : Galaxy S25 (Snapdragon 8 Gen 3)  
- **On-device AI** : Qualcomm AI Hub 활용  
- **Android Native App**  
- AI 모델 최적화 및 TensorFlow Lite / Qualcomm SDK 연동  

---

## 📖 팀 정보

- **팀** : 12팀 Wakey
- **팀원** : 2271134 양준영(팀장), 2071038 남윤창, 2271414 최은서, 2271102 강민서  
- **프로젝트명** : Wakey Wakey
- **소속** : 2025 캡스톤디자인 퀄컴  
