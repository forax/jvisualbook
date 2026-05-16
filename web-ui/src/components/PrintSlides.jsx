import { useEffect, useRef } from 'react';
import ReactDOM from 'react-dom/client';
import ReactMarkdown from 'react-markdown';
import './PrintSlides.css';

/**
 * Renders a hidden DOM tree with one .print-slide per section,
 * then triggers window.print(). Cleaned up after printing.
 */
export function printSlidesAsPDF(doc) {
  // Create a container outside the React root
  const container = document.createElement('div');
  container.style.display = 'none'; // hidden until print media query takes over
  document.body.appendChild(container);

  const root = ReactDOM.createRoot(container);
  root.render(<PrintSlides doc={doc} onReady={() => {
    window.onafterprint = () => { root.unmount(); container.remove(); };
    window.print();
  }} />);
}

function PrintSlides({ doc, onReady }) {
  const triggered = useRef(false);

  useEffect(() => {
    if (triggered.current) return;
    triggered.current = true;
    // Give React one frame to finish rendering before printing
    requestAnimationFrame(() => requestAnimationFrame(onReady));
  }, [onReady]);

  return (
    <>
      {doc.sections.map((section, i) => (
        <div key={i} className="print-slide">
          {section.title && (
            <ReactMarkdown className="print-slide-title">
              {section.title}
            </ReactMarkdown>
          )}
          <div className="print-slide-body">
            {section.contents.map((content, j) => {
              if (content.kind === 'TEXT') {
                return (
                  <ReactMarkdown key={j}>{content.text}</ReactMarkdown>
                );
              }
              if (content.kind === 'CODE') {
                return <pre key={j}>{content.text}</pre>;
              }
              if (content.kind === 'OUTPUT' && content.text) {
                const cls = content.status === 'ERROR' ? 'print-output error' : 'print-output';
                return <pre key={j} className={cls}>{content.text}</pre>;
              }
              return null;
            })}
          </div>
        </div>
      ))}
    </>
  );
}