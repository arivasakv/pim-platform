import { useState } from 'react'
import {
  Button,
  Card,
  CheckboxField,
  Notification,
  PageHeader,
  SelectField,
  StatusBadge,
  TextAreaField,
  TextField,
} from '../components/ui'
import styles from './ComponentGalleryPage.module.css'

// A long string without spaces, to show that content wraps instead of overflowing (US4-AS3).
const LONG_UNBROKEN_TEXT = 'REFERENCIAINTERNASINESPACIOS'.repeat(6)

/** Visual reference of the reusable components and their states (FR-012). */
export function ComponentGalleryPage() {
  const [isNotificationVisible, setIsNotificationVisible] = useState(true)
  const [exampleOption, setExampleOption] = useState('')
  const [exampleChecked, setExampleChecked] = useState(false)

  return (
    <>
      <PageHeader
        title="Componentes"
        description="Referencia visual de los componentes reutilizables de la plataforma."
      />

      <div className={styles.groups}>
        <Card title="Botones">
          <div className={styles.row}>
            <Button>Principal</Button>
            <Button variant="secondary">Secundario</Button>
            <Button icon="search">Con icono</Button>
            <Button disabled>Principal deshabilitado</Button>
            <Button variant="secondary" disabled>
              Secundario deshabilitado
            </Button>
          </div>
        </Card>

        <Card title="Campos de texto">
          <div className={styles.stack}>
            <TextField
              id="ejemplo-nombre"
              label="Nombre"
              placeholder="Escribe un nombre"
              hint="Usa el nombre con el que se identifica internamente."
            />
            <TextField id="ejemplo-codigo" label="Código" error="Campo obligatorio" />
            <TextField
              id="ejemplo-deshabilitado"
              label="Campo deshabilitado"
              defaultValue="No editable"
              disabled
            />
            <TextField
              id="ejemplo-busqueda"
              label="Buscar"
              type="search"
              hideLabel
              placeholder="Buscar"
            />
          </div>
        </Card>

        <Card title="Listas, casillas y textos largos">
          <div className={styles.stack}>
            <SelectField
              id="ejemplo-lista"
              label="Lista de opciones"
              placeholder="Elige una opción"
              options={[
                { code: 'A', label: 'Opción A' },
                { code: 'B', label: 'Opción B' },
              ]}
              value={exampleOption}
              onChange={setExampleOption}
              hint="Las opciones vienen del servidor cuando son datos del negocio."
            />
            <CheckboxField
              id="ejemplo-casilla"
              label="Casilla de verificación"
              checked={exampleChecked}
              onChange={setExampleChecked}
            />
            <TextAreaField
              id="ejemplo-texto-largo"
              label="Texto largo"
              placeholder="Escribe una descripción"
              maxLength={1000}
            />
          </div>
        </Card>

        <Card title="Tarjetas">
          <div className={styles.stack}>
            <Card
              title="Tarjeta con título y acciones"
              actions={<Button variant="secondary">Editar</Button>}
            >
              <p className={styles.text}>
                Las tarjetas agrupan contenido relacionado dentro de una página.
              </p>
            </Card>
            <Card>
              <p className={styles.text}>Tarjeta sin título, para contenido sencillo.</p>
            </Card>
          </div>
        </Card>

        <Card title="Títulos">
          <div className={styles.stack}>
            <p className={styles.text}>
              El encabezado «Componentes» de esta página es el título de página (uno por página).
              Los títulos de cada tarjeta, como «Títulos», son encabezados de sección.
            </p>
            <Card title="Un título de sección muy largo que ocupa más de una línea cuando el espacio disponible es reducido, por ejemplo en una tableta">
              <p className={styles.text}>{LONG_UNBROKEN_TEXT}</p>
            </Card>
          </div>
        </Card>

        <Card title="Estados y notificaciones">
          <div className={styles.stack}>
            <div className={styles.row}>
              <StatusBadge variant="success">Completo</StatusBadge>
              <StatusBadge variant="warning">Pendiente de revisión</StatusBadge>
              <StatusBadge variant="error">Con errores</StatusBadge>
              <StatusBadge variant="info">En proceso</StatusBadge>
              <StatusBadge variant="neutral">Borrador</StatusBadge>
            </div>
            <Notification variant="success" title="Cambios guardados">
              La información se guardó correctamente.
            </Notification>
            <Notification variant="warning">Hay campos que conviene revisar.</Notification>
            <Notification variant="error">No se pudo completar la operación.</Notification>
            {isNotificationVisible && (
              <Notification
                variant="info"
                title="Aviso"
                onDismiss={() => setIsNotificationVisible(false)}
              >
                Esta notificación se puede cerrar.
              </Notification>
            )}
          </div>
        </Card>
      </div>
    </>
  )
}
