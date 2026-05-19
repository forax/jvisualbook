const API_BASE_URL = '/api';

export async function fetchChapters() {
  const response = await fetch(`${API_BASE_URL}/chapter`, {
    headers: {
      'Accept': 'application/json',
    }
  });
  if (!response.ok) {
    throw Error(`Failed to fetch chapters, status: ${response.status}`);
  }
  return await response.json();
}

export async function fetchChapterDocument(chapterName) {
  const response = await fetch(`${API_BASE_URL}/chapter/${encodeURIComponent(chapterName)}`, {
    headers: {
      'Accept': 'application/json',
    }
  });
  if (!response.ok) {
    throw Error(`Failed to fetch document for chapter: ${chapterName} status: ${response.status}`);
  }
  return await response.json();
}

export async function postCode(program, signal) {
  const response = await fetch(`${API_BASE_URL}/code`, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(program),
    signal
  });
  if (!response.ok) {
    throw Error(`Failed to post program, status: ${response.status}`);
  }
  return await response.json();
}