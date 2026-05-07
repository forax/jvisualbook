import React, { useState, useEffect, useCallback } from 'react';
import ReactMarkdown from 'react-markdown';
import './SlideViewer.css';

function SlideViewer({ doc, chapterName, onExit, renderContent }) {
  const sections = doc?.sections ?? [];
  const [idx, setIdx] = useState(0);

  const prev = useCallback(() => setIdx(i => Math.max(0, i - 1)), []);
  const next = useCallback(() => setIdx(i => Math.min(sections.length - 1, i + 1)), [sections.length]);

  useEffect(() => {
    const onKey = e => {
      if (e.key === 'ArrowLeft')  prev();
      if (e.key === 'ArrowRight') next();
      if (e.key === 'Escape')     onExit();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [prev, next, onExit]);

  if (!sections.length) return null;

  const section = sections[idx];

  return (
    <div className="slide-overlay">
      <div className="slide-stage">
        <button
          className="slide-nav slide-nav-prev"
          onClick={prev}
          disabled={idx === 0}
          aria-label="Previous slide"
        >&#8249;</button>

        <div className="slide-card">
          {section.title && (
            <ReactMarkdown className="slide-title">{section.title}</ReactMarkdown>
          )}
          <div className="slide-body">
            {section.contents.map(c => renderContent(c))}
          </div>
        </div>

        <button
          className="slide-nav slide-nav-next"
          onClick={next}
          disabled={idx === sections.length - 1}
          aria-label="Next slide"
        >&#8250;</button>
      </div>

      <div className="slide-bottombar">
        <span className="slide-chapter">{chapterName}</span>
        <div className="slide-dots">
          {sections.map((_, i) => (
            <button
              key={i}
              className={`slide-dot${i === idx ? ' active' : ''}`}
              onClick={() => setIdx(i)}
              aria-label={`Go to slide ${i + 1}`}
            />
          ))}
        </div>
        <span className="slide-counter">{idx + 1} / {sections.length}</span>
      </div>
    </div>
  );
}

export default SlideViewer;
