const API_BASE_URL = '/api';

export async function fetchChapters() {
  const response = await fetch(`${API_BASE_URL}/chapter`, {
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch chapters: ${response.statusText}`);
  }
  return await response.json();
}

export async function fetchChapterDocument(chapterName) {
  const response = await fetch(`${API_BASE_URL}/chapter/${encodeURIComponent(chapterName)}`, {
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    }
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch document for chapter: ${chapterName}`);
  }
  return await response.json();
}

export async function postCode(code) {
  const response = await fetch(`${API_BASE_URL}/code`, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(code)
  });
  if (!response.ok) {
    throw new Error(`Failed to post code : ${JSON.stringify(code)}`);
  }
  return await response.json();
}