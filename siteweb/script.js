const translations = {
    en: {
        heroTitle: "Professional Streaming Experience",
        heroSubtitle: "Watch your favorite live events and sports channels in high quality. Fast, reliable, and always up to date.",
        downloadBtn: "Download APK",
        viewGalleryBtn: "View Gallery",
        featuresTitle: "Key Features",
        feat1Title: "Ultra Fast",
        feat1Desc: "No more buffering. High-speed streaming engines for a smooth experience.",
        feat2Title: "HD Quality",
        feat2Desc: "Crystal clear video quality for all your matches and events.",
        feat3Title: "Multi-Language",
        feat3Desc: "UI translated in English, Arabic, and Spanish for everyone.",
        galleryTitle: "App Screenshots",
        copyright: "© 2026 Mourad TV. All Rights Reserved."
    },
    ar: {
        heroTitle: "تجربة بث احترافية",
        heroSubtitle: "شاهد الأحداث المباشرة والقنوات الرياضية المفضلة لديك بجودة عالية. سريع، موثوق، ومحدث دائماً.",
        downloadBtn: "تحميل APK",
        viewGalleryBtn: "معرض الصور",
        featuresTitle: "المميزات الرئيسية",
        feat1Title: "سرعة فائقة",
        feat1Desc: "لا مزيد من التقطيع. محركات بث عالية السرعة لتجربة سلسة.",
        feat2Title: "جودة HD",
        feat2Desc: "جودة فيديو واضحة تماماً لجميع المباريات والأحداث.",
        feat3Title: "متعدد اللغات",
        feat3Desc: "واجهة مترجمة للغات الإنجليزية، العربية، والإسبانية للجميع.",
        galleryTitle: "لقطات من التطبيق",
        copyright: "© 2026 Mourad TV. جميع الحقوق محفوظة."
    },
    es: {
        heroTitle: "Experiencia de Streaming Profesional",
        heroSubtitle: "Mira tus eventos en vivo y canales de deportes favoritos en alta calidad. Rápido, confiable y siempre actualizado.",
        downloadBtn: "Descargar APK",
        viewGalleryBtn: "Ver Galería",
        featuresTitle: "Características Principales",
        feat1Title: "Ultra Rápido",
        feat1Desc: "No más buffering. Motores de streaming de alta velocidad para una experiencia fluida.",
        feat2Title: "Calidad HD",
        feat2Desc: "Calidad de video cristalina para todos tus partidos y eventos.",
        feat3Title: "Multilingüe",
        feat3Desc: "Interfaz traducida al inglés, árabe y español para todos.",
        galleryTitle: "Capturas de Pantalla",
        copyright: "© 2026 Mourad TV. Todos los derechos reservados."
    }
};

const langSelect = document.getElementById('langSelect');

function updateLanguage(lang) {
    document.body.classList.toggle('rtl', lang === 'ar');
    document.documentElement.lang = lang;

    const t = translations[lang];
    for (const key in t) {
        const el = document.getElementById(key);
        if (el) {
            el.innerText = t[key];
        }
    }
}

langSelect.addEventListener('change', (e) => {
    updateLanguage(e.target.value);
    localStorage.setItem('preferredLang', e.target.value);
});

// Init from local storage or default
const savedLang = localStorage.getItem('preferredLang') || 'en';
langSelect.value = savedLang;
updateLanguage(savedLang);

// Simple AOS effect logic
window.addEventListener('scroll', () => {
    const reveals = document.querySelectorAll('[data-aos]');
    reveals.forEach(el => {
        const windowHeight = window.innerHeight;
        const revealTop = el.getBoundingClientRect().top;
        const revealPoint = 150;

        if (revealTop < windowHeight - revealPoint) {
            el.classList.add('aos-active');
        }
    });
});
