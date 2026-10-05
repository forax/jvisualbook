import { useEffect, useRef } from 'react';
import ReactDOM from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import './PrintSlides.css';

/**
 * Renders a hidden DOM tree with one .print-slide per section,
 * then triggers window.print(). Cleaned up after printing.
 */
export function printSlidesAsPDF(doc) {
  // Create (or reuse) a container outside the React root
  let container = document.getElementById('print-slides-root');
  if (!container) {
    container = document.createElement('div');
    container.id = 'print-slides-root';
    container.style.display = 'none'; // hidden until print media query takes over
    document.body.appendChild(container);
  }

  const root = ReactDOM.createRoot(container);
  const cleanup = () => {
    document.body.classList.remove('printing');
    root.unmount();
    container.remove();
  };
  root.render(<PrintSlides doc={doc} onReady={() => {
    document.body.classList.add('printing');
    window.addEventListener('afterprint', cleanup, { once: true });
    window.print();
  }} />);
}

function PrintSlides({ doc, onReady }) {
  const triggeredRef = useRef(false);

  useEffect(() => {
    if (triggeredRef.current) return;
    triggeredRef.current = true;
    // Give React one frame to finish rendering before printing
    requestAnimationFrame(() => requestAnimationFrame(onReady));
  }, [onReady]);

  return (
    <>
      {doc.sections.map(section => (
        <div key={section.id} className="print-slide">
          {section.title && (
            <ReactMarkdown className="print-slide-title">
              {section.title}
            </ReactMarkdown>
          )}
          <div className="print-slide-body">
            {section.contents.map(content => {
              if (content.kind === 'TEXT') {
                return (
                  <ReactMarkdown key={content.id}>{content.text}</ReactMarkdown>
                );
              }
              if (content.kind === 'CODE') {
                return <pre key={content.id}>{content.text}</pre>;
              }
              if (content.kind === 'OUTPUT' && content.text) {
                const cls = content.status === 'ERROR' ? 'print-output error' : 'print-output';
                return <pre key={content.id} className={cls}>{content.text}</pre>;
              }
              return null;
            })}
          </div>
        </div>
      ))}
    </>
  );
}