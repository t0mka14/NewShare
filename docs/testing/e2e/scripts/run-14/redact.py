"""DOM redaction helper: replace installation-id UUIDs (full or the 8-hex prefix + ellipsis) in visible text before a screenshot."""
REDACT_JS = r"""()=>{const re=/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|\b[0-9a-f]{8}(?:…|\.\.\.)/gi;
const w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;while(n=w.nextNode()){re.lastIndex=0;if(re.test(n.nodeValue)){re.lastIndex=0;n.nodeValue=n.nodeValue.replace(re,'<installationId>')}}}"""
def redact(page): page.evaluate(REDACT_JS)
