export function NoDocumentSelected(): JSX.Element {
  return (
    <div className="empty empty--main">
      <p className="empty__emoji" aria-hidden="true">
        ⌘
      </p>
      <h1 className="empty__title">Pick a document to start chatting</h1>
      <p className="empty__text">
        Upload a PDF from the sidebar, then ask anything about it. Every answer cites the pages it
        came from.
      </p>
    </div>
  );
}
