import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { LettreService } from '../services/lettre.service';
import { AiProvider, AiLetterResult } from '../models/lettre.model';

/**
 * Création d'une lettre avec l'IA (générer à partir du CV + offre, puis améliorer).
 * Route « nouvelle » : création. Route avec :id : modification d'une lettre existante.
 * L'IA ne sauvegarde rien : l'utilisateur relit, modifie, puis clique sur « Enregistrer ».
 */
@Component({
  selector: 'app-lettre-generator',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './lettre-generator.component.html',
  styleUrl: './lettre-generator.component.scss'
})
export class LettreGeneratorComponent implements OnInit {
  readonly tones = [
    { value: 'professionnel', label: 'Professionnel' },
    { value: 'dynamique', label: 'Dynamique' },
    { value: 'chaleureux', label: 'Chaleureux' },
    { value: 'concis', label: 'Concis' }
  ];
  readonly maxFileBytes = 5 * 1024 * 1024;

  editId: number | null = null;

  // Formulaire
  nomEntreprise = '';
  poste = '';
  offre = '';
  cv = '';
  ton = 'professionnel';
  langue = 'fr';
  provider = '';
  providers: AiProvider[] = [];

  // Lettre et retour de l'IA
  contenu = '';
  instruction = '';
  score: number | null = null;
  suggestions: string[] = [];
  usedModel: string | null = null;

  // États
  extracting = false;
  generating = false;
  improving = false;
  saving = false;
  loadingLetter = false;
  error: string | null = null;
  info: string | null = null;

  constructor(
    private lettreService: LettreService,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.lettreService.aiProviders().subscribe({
      next: (res) => {
        this.providers = res.data ?? [];
        this.provider = this.providers.find(p => p.preferred && p.configured)?.name
          ?? this.providers.find(p => p.configured)?.name ?? '';
        this.cdr.detectChanges();
      },
      error: () => { /* la liste reste vide : le fournisseur par défaut du serveur sera utilisé */ }
    });

    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      this.editId = id;
      this.loadingLetter = true;
      this.lettreService.getById(id).subscribe({
        next: (res) => {
          this.loadingLetter = false;
          if (res.success && res.data) {
            this.nomEntreprise = res.data.nomEntreprise;
            this.poste = res.data.poste ?? '';
            this.cv = res.data.cv;
            this.contenu = res.data.contenu;
          } else {
            this.error = res.message;
          }
          this.cdr.detectChanges();
        },
        error: () => {
          this.loadingLetter = false;
          this.error = 'Impossible de charger cette lettre.';
          this.cdr.detectChanges();
        }
      });
    }
  }

  get canGenerate(): boolean {
    return !!(this.nomEntreprise.trim() && this.offre.trim() && this.cv.trim()) && !this.busy;
  }
  get canSave(): boolean {
    return !!(this.nomEntreprise.trim() && this.cv.trim() && this.contenu.trim()) && !this.busy;
  }
  get busy(): boolean {
    return this.extracting || this.generating || this.improving || this.saving;
  }

  // ── CV : import d'un fichier ──
  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.clearMessages();

    if (file.size > this.maxFileBytes) {
      this.error = 'Fichier trop volumineux (5 Mo maximum).';
      return;
    }
    this.extracting = true;
    this.lettreService.aiExtractCv(file).subscribe({
      next: (res) => {
        this.extracting = false;
        if (res.success) {
          this.cv = res.data ?? '';
          this.info = 'CV importé. Relisez le texte avant de générer.';
        } else {
          this.error = res.message;
        }
        this.cdr.detectChanges();
      },
      error: (err) => this.fail('extracting', err, 'Impossible de lire ce fichier.')
    });
  }

  // ── Générer ──
  generate(): void {
    if (!this.canGenerate) return;
    this.clearMessages();
    this.generating = true;
    this.lettreService.aiGenerate({
      nomEntreprise: this.nomEntreprise.trim(),
      poste: this.poste.trim() || undefined,
      offre: this.offre.trim(),
      cv: this.cv.trim(),
      ton: this.ton,
      langue: this.langue,
      provider: this.provider || undefined
    }).subscribe({
      next: (res) => {
        this.generating = false;
        if (res.success && res.data) {
          this.applyResult(res.data, false);
          this.info = 'Brouillon généré. Complétez la signature, relisez, puis enregistrez.';
        } else {
          this.error = res.message;
        }
        this.cdr.detectChanges();
      },
      error: (err) => this.fail('generating', err, 'La génération a échoué.')
    });
  }

  // ── Améliorer ──
  improve(): void {
    if (!this.contenu.trim() || this.busy) return;
    this.clearMessages();
    this.improving = true;
    this.lettreService.aiImprove({
      contenu: this.contenu.trim(),
      offre: this.offre.trim() || undefined,
      instruction: this.instruction.trim() || undefined,
      provider: this.provider || undefined
    }).subscribe({
      next: (res) => {
        this.improving = false;
        if (res.success && res.data) {
          this.applyResult(res.data, true);
          this.instruction = '';
        } else {
          this.error = res.message;
        }
        this.cdr.detectChanges();
      },
      error: (err) => this.fail('improving', err, "L'amélioration a échoué.")
    });
  }

  // ── Enregistrer ──
  save(): void {
    if (!this.canSave) return;
    this.clearMessages();
    this.saving = true;
    const body = {
      nomEntreprise: this.nomEntreprise.trim(),
      poste: this.poste.trim() || undefined,
      cv: this.cv.trim(),
      contenu: this.contenu.trim()
    };
    const call = this.editId
      ? this.lettreService.update(this.editId, body)
      : this.lettreService.create(body);

    call.subscribe({
      next: (res) => {
        this.saving = false;
        if (res.success) {
          if (res.data) this.editId = res.data.id;   // les enregistrements suivants mettent à jour la même lettre
          this.info = res.message;
        } else {
          this.error = res.message;
        }
        this.cdr.detectChanges();
      },
      error: (err) => this.fail('saving', err, "Impossible d'enregistrer la lettre.")
    });
  }

  // ── Utilitaires ──
  private applyResult(r: AiLetterResult, withFeedback: boolean): void {
    this.contenu = r.contenu;
    this.usedModel = `${r.provider} · ${r.model}`;
    this.score = withFeedback ? (r.score ?? null) : null;
    this.suggestions = withFeedback ? (r.suggestions ?? []) : [];
  }

  private fail(flag: 'extracting' | 'generating' | 'improving' | 'saving', err: any, fallback: string): void {
    this[flag] = false;
    this.error = err?.error?.message || fallback;
    this.cdr.detectChanges();
  }

  private clearMessages(): void {
    this.error = null;
    this.info = null;
  }
}
