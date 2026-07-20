import {
  ArrowRight,
  BrainCircuit,
  GitBranch,
  Network,
  ScanSearch,
  Sparkles
} from "lucide-react";
import { RepoUrlForm } from "@/components/repo-url-form/repo-url-form";
import { RecentWorkspaces } from "@/components/recent-workspaces/recent-workspaces";

export default function HomePage() {
  return (
    <main className="landing-page">
      <div className="landing-noise" />
      <header className="landing-nav">
        <a href="/" className="landing-brand">
          <span><GitBranch size={19} /></span>
          <strong>GitHub Graph</strong>
        </a>
        <nav>
          <a href="#capabilities">Capabilities</a>
          <a href="#workflow">How it works</a>
        </nav>
        <span className="build-status"><i /> Python analysis live</span>
      </header>

      <section className="landing-hero">
        <div className="landing-copy">
          <p className="hero-kicker"><Sparkles size={14} /> Repository intelligence, grounded in code</p>
          <h1>
            See the architecture
            <span>hidden in every repo.</span>
          </h1>
          <p className="hero-lead">
            Convert Python repositories into explorable code graphs. Trace dependencies,
            measure impact, compare functions and localize failures with evidence.
          </p>
          <RepoUrlForm />
          <div className="hero-proof">
            <span>Static extraction</span><i />
            <span>Graph analytics</span><i />
            <span>Grounded AI</span>
          </div>
        </div>

        <div className="landing-visual" aria-label="Illustration of an interactive code graph">
          <div className="visual-window">
            <div className="window-bar">
              <span><i /><i /><i /></span>
              <strong>auth_service.py</strong>
              <em>LIVE GRAPH</em>
            </div>
            <svg className="visual-edges" viewBox="0 0 560 490" aria-hidden="true">
              <path d="M96 106 C180 100, 175 212, 260 208" />
              <path d="M96 106 C190 120, 298 84, 388 110" />
              <path d="M260 208 C350 205, 350 306, 441 304" />
              <path d="M260 208 C225 270, 180 294, 157 351" />
              <path d="M157 351 C250 380, 350 382, 441 304" />
              <path d="M388 110 C420 160, 426 230, 441 304" />
            </svg>
            <div className="preview-node node-file"><span>F</span><strong>auth.py</strong><small>Python file</small></div>
            <div className="preview-node node-function"><span>Fn</span><strong>login</strong><small>function</small></div>
            <div className="preview-node node-module"><span>M</span><strong>jwt</strong><small>module</small></div>
            <div className="preview-node node-class"><span>C</span><strong>User</strong><small>class</small></div>
            <div className="preview-node node-api"><span>API</span><strong>/login</strong><small>POST route</small></div>
            <div className="visual-tooltip">
              <small>IMPACT SCORE</small><strong>0.84</strong><span>12 downstream nodes</span>
            </div>
            <div className="visual-legend"><i /><span>calls</span><i /><span>uses</span><i /><span>belongs to</span></div>
          </div>
          <div className="floating-stat stat-one"><strong>349</strong><span>graph nodes</span></div>
          <div className="floating-stat stat-two"><strong>6</strong><span>algorithms</span></div>
        </div>
      </section>

      <section id="capabilities" className="capability-strip">
        <article>
          <span><Network size={21} /></span>
          <div><strong>Explore the graph</strong><p>Pan, search, filter and inspect every code relationship.</p></div>
          <ArrowRight size={17} />
        </article>
        <article>
          <span><GitBranch size={21} /></span>
          <div><strong>Trace blast radius</strong><p>Follow dependencies and see what a failure can affect.</p></div>
          <ArrowRight size={17} />
        </article>
        <article>
          <span><ScanSearch size={21} /></span>
          <div><strong>Localize failures</strong><p>Rank likely root causes with explainable evidence.</p></div>
          <ArrowRight size={17} />
        </article>
        <article>
          <span><BrainCircuit size={21} /></span>
          <div><strong>Ask grounded questions</strong><p>Get cited answers based on graph analysis, not guesses.</p></div>
          <ArrowRight size={17} />
        </article>
      </section>

      <RecentWorkspaces />

      <section id="workflow" className="workflow-section">
        <div>
          <p className="section-kicker">From URL to intelligence</p>
          <h2>One repository. Four clear steps.</h2>
        </div>
        <ol>
          <li><span>01</span><strong>Ingest</strong><p>Validate and clone a bounded public snapshot.</p></li>
          <li><span>02</span><strong>Extract</strong><p>Parse files, classes, functions, calls and routes.</p></li>
          <li><span>03</span><strong>Connect</strong><p>Build a stable Neo4j dependency model.</p></li>
          <li><span>04</span><strong>Explore</strong><p>Analyze impact, similarity, failures and flows.</p></li>
        </ol>
      </section>
    </main>
  );
}
