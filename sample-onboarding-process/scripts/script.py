import requests
import time
import random
import string

def generate_business_key():
    # Gera uma string aleatória de 7 caracteres (letras minúsculas e números)
    suffix = ''.join(random.choices(string.ascii_lowercase + string.digits, k=7))
    return f"kyc-{suffix}"

def run_load_test():
    url = 'http://localhost:8081/kikwiflow/api/v1/process-instances'
    headers = {
        'Accept': '*/*',
        'Content-Type': 'application/json',
        'Origin': 'http://localhost:3000'
    }

    print("Iniciando geração de 1000 instâncias no Kikwiflow...")

    for i in range(1000):
        # 1. Intercala o taxId (1, 2, 3, 4)
        tax_id = random.randint(0, 4)
        
        # 2. Gera o Business Key Randômico
        business_key = generate_business_key()
        
        # 3. Monta o Payload seguindo a estrutura exata do seu cURL
        payload = {
            "processDefinitionKey": "kyc-emissao-parecer",
            "origin": "whatsapp",
            "businessKey": business_key,
            "businessValue": 12355,
            "variables": {
                "taxId": {
                    "name": "taxId",
                    "visibility": "PUBLIC",
                    "roles": [],
                    "isTransient": False,
                    "value": str(tax_id)
                }
            }
        }

        try:
            # Envia a requisição POST (o parâmetro 'json' já faz o dump e ajusta os headers automaticamente)
            response = requests.post(url, json=payload, headers=headers)
            print(f"[{i + 1}/1000] HTTP {response.status_code} | taxId: {tax_id} | BK: {business_key}")
            
        except requests.exceptions.RequestException as e:
            print(f"[{i + 1}/1000] Falha de conexão: {e}")
        
        # Breve pausa (20ms) para não derrubar as threads do servidor local
        time.sleep(0.02)

    print("\nCarga de testes concluída com sucesso!")

if __name__ == "__main__":
    run_load_test()