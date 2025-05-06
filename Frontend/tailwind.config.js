/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,js,jsx,ts,tsx,vue,hbs,mustache,thymeleaf}", // Frontend 개발 파일
    "../SpringProject/src/main/resources/templates/**/*.{html,thymeleaf}", // Spring Boot 템플릿 파일 경로 (확장자에 따라 수정)
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}

