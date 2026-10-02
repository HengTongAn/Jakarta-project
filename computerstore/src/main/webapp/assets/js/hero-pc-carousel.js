/* Rotating storefront hero photos: one real desktop PC per brand. */
(function () {
    'use strict';

    var carousel = document.querySelector('[data-hero-pc-carousel]');
    if (!carousel) return;

    var slides = Array.prototype.slice.call(carousel.querySelectorAll('[data-hero-pc-slide]'));
    if (slides.length < 2) return;

    var dots = Array.prototype.slice.call(carousel.querySelectorAll('[data-hero-pc-to]'));
    var count = carousel.querySelector('[data-hero-pc-count]');
    var previous = carousel.querySelector('[data-hero-pc-prev]');
    var next = carousel.querySelector('[data-hero-pc-next]');
    var reduceMotion =
        window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var activeIndex = 0;
    var timer = null;
    var hovering = false;
    var focusing = false;

    function show(index) {
        activeIndex = (index + slides.length) % slides.length;
        slides.forEach(function (slide, slideIndex) {
            var active = slideIndex === activeIndex;
            slide.classList.toggle('is-active', active);
            slide.setAttribute('aria-hidden', String(!active));
            if (active) {
                slide.removeAttribute('tabindex');
                slide.setAttribute('aria-current', 'true');
            } else {
                slide.setAttribute('tabindex', '-1');
                slide.removeAttribute('aria-current');
            }
        });
        dots.forEach(function (dot, dotIndex) {
            var active = dotIndex === activeIndex;
            dot.classList.toggle('is-active', active);
            if (active) dot.setAttribute('aria-current', 'true');
            else dot.removeAttribute('aria-current');
        });
        if (count) count.textContent = activeIndex + 1 + ' / ' + slides.length;
    }

    function stop() {
        if (timer !== null) {
            window.clearInterval(timer);
            timer = null;
        }
    }

    function start() {
        stop();
        if (!reduceMotion && !hovering && !focusing && !document.hidden) {
            timer = window.setInterval(function () {
                show(activeIndex + 1);
            }, 5000);
        }
    }

    if (previous)
        previous.addEventListener('click', function () {
            show(activeIndex - 1);
            start();
        });
    if (next)
        next.addEventListener('click', function () {
            show(activeIndex + 1);
            start();
        });
    dots.forEach(function (dot) {
        dot.addEventListener('click', function () {
            show(Number(dot.getAttribute('data-hero-pc-to')));
            start();
        });
    });

    carousel.addEventListener('mouseenter', function () {
        hovering = true;
        stop();
    });
    carousel.addEventListener('mouseleave', function () {
        hovering = false;
        start();
    });
    carousel.addEventListener('focusin', function () {
        focusing = true;
        stop();
    });
    carousel.addEventListener('focusout', function (event) {
        if (!carousel.contains(event.relatedTarget)) {
            focusing = false;
            start();
        }
    });
    document.addEventListener('visibilitychange', start);

    show(0);
    start();
})();
