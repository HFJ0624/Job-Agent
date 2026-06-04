<template>
  <main class="page-section">
    <section v-if="!authStore.isLogin" class="resume-board">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1>请先登录</h1>
        <p>登录后可以查看和修改当前用户资料。</p>
      </div>
      <RouterLink class="primary-button large" to="/login?redirect=/profile">去登录</RouterLink>
    </section>

    <section v-else class="profile-card">
      <div class="section-heading">
        <div>
          <p class="eyebrow">个人中心</p>
          <h1>我的资料</h1>
        </div>
        <span class="match-score">账号：{{ authStore.user?.username }}</span>
      </div>

      <form class="form-grid" @submit.prevent="submit">
        <div class="profile-avatar-panel full-line">
          <button class="profile-avatar-button" type="button" @click="openAvatarPicker">
            <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="用户头像" />
            <span v-else>{{ avatarText }}</span>
            <small>{{ avatarUploading ? "上传中..." : "点击修改头像" }}</small>
          </button>
          <input ref="avatarInput" class="hidden-file-input" type="file" accept="image/*" @change="changeAvatar" />
          <div>
            <h3>{{ authStore.displayName }}</h3>
            <p>点击头像即可重新选择图片，上传成功后会自动保存到 MinIO 和个人资料。</p>
          </div>
        </div>

        <label>
          <span>昵称</span>
          <input v-model="form.nickname" placeholder="新昵称" />
        </label>
        <label>
          <span>真实姓名</span>
          <input v-model="form.realName" placeholder="张三" />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" placeholder="13800000001" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model="form.email" type="email" placeholder="new@example.com" />
        </label>
        <label>
          <span>性别</span>
          <select v-model.number="form.gender">
            <option :value="0">未知</option>
            <option :value="1">男</option>
            <option :value="2">女</option>
          </select>
        </label>
        <label>
          <span>学历</span>
          <input v-model="form.education" placeholder="本科" />
        </label>
        <label>
          <span>工作年限</span>
          <input v-model.number="form.workYears" min="0" step="0.5" type="number" />
        </label>

        <p v-if="errorMessage" class="form-error full-line">{{ errorMessage }}</p>
        <p v-if="successMessage" class="form-success full-line">{{ successMessage }}</p>
        <button class="primary-button large full-line" :disabled="avatarUploading">保存资料</button>
      </form>

      <div class="profile-divider"></div>

      <section class="address-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">家庭地址</p>
            <h2>我的地址</h2>
          </div>
          <button class="text-button" type="button" :disabled="addressLoading" @click="loadAddress">
            {{ addressLoading ? "加载中..." : "刷新地址" }}
          </button>
        </div>

        <div class="amap-picker">
          <div class="address-map-toolbar">
            <div>
              <strong>地图选址</strong>
              <span>{{ amapConfigTip }}</span>
            </div>
            <button class="primary-button" type="button" @click="openMapPicker">打开地图选择</button>
          </div>
          <div ref="mapPreviewRef" class="address-map-preview">
            <div v-if="!getAmapKey()" class="map-empty-state">
              配置高德 Key 后这里会展示地图预览，也可以继续手动填写地址。
            </div>
          </div>
        </div>

        <div v-if="mapPickerOpen" class="map-modal-mask">
          <section class="map-modal">
            <header class="map-modal-header">
              <div>
                <p class="eyebrow">高德地图</p>
                <h3>选择家庭地址</h3>
              </div>
              <button class="text-button" type="button" @click="closeMapPicker">关闭</button>
            </header>

            <div class="map-modal-search">
              <input v-model.trim="amapKeyword" placeholder="搜索小区、学校、公司或详细地址" @keyup.enter="searchAmapAddress" />
              <button class="primary-button" type="button" :disabled="amapSearching" @click="searchAmapAddress">
                {{ amapSearching ? "搜索中..." : "搜索地点" }}
              </button>
            </div>

            <div ref="mapPickerRef" class="map-picker-canvas"></div>
            <p class="address-hint">可以搜索地点后点击结果，也可以直接点击地图上的位置来选择地址。</p>

            <div v-if="amapPois.length" class="amap-result-list map-result-list">
              <button v-for="poi in amapPois" :key="poi.id || `${poi.name}-${poi.address}`" type="button" @click="selectAmapPoi(poi)">
                <strong>{{ poi.name }}</strong>
                <span>{{ formatPoiAddress(poi) }}</span>
              </button>
            </div>

            <div class="map-modal-actions">
              <span>{{ selectedMapText }}</span>
              <button class="primary-button" type="button" @click="confirmMapSelection">使用这个地址</button>
            </div>
          </section>
        </div>
        <form class="form-grid address-form" @submit.prevent="submitAddress">
          <label>
            <span>地址名称</span>
            <input v-model="addressForm.addressName" maxlength="64" placeholder="家" />
          </label>
          <label>
            <span>省份</span>
            <input v-model="addressForm.province" maxlength="64" placeholder="辽宁省" />
          </label>
          <label>
            <span>城市</span>
            <input v-model="addressForm.city" maxlength="64" placeholder="沈阳市" />
          </label>
          <label>
            <span>区县</span>
            <input v-model="addressForm.district" maxlength="64" placeholder="浑南区" />
          </label>
          <label class="full-line">
            <span>详细地址</span>
            <input v-model="addressForm.detailAddress" maxlength="255" placeholder="街道、小区、楼栋和门牌号" />
          </label>
          <label>
            <span>经度</span>
            <input v-model="addressForm.longitude" placeholder="高德选择后自动填充" />
          </label>
          <label>
            <span>纬度</span>
            <input v-model="addressForm.latitude" placeholder="高德选择后自动填充" />
          </label>

          <p v-if="addressError" class="form-error full-line">{{ addressError }}</p>
          <p v-if="addressSuccess" class="form-success full-line">{{ addressSuccess }}</p>
          <button class="primary-button large full-line" :disabled="addressSaving">
            {{ addressSaving ? "保存中..." : "保存家庭地址" }}
          </button>
        </form>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { getDefaultAddress, saveDefaultAddress } from "../api/address";
import { uploadAvatar } from "../api/file";
import type { UserAddressInfo } from "../api/types";
import { updateProfile } from "../api/user";
import { useAuthStore } from "../stores/auth";
import { avatarInitial, normalizeAvatarUrl } from "../utils/avatar";

type AmapLocation = {
  lng?: number;
  lat?: number;
  getLng?: () => number;
  getLat?: () => number;
};

type AmapClickEvent = {
  lnglat: AmapLocation;
};

type AmapAddressComponent = {
  province?: string;
  city?: string | string[];
  district?: string;
  township?: string;
  street?: string;
  streetNumber?: string;
};

type AmapRegeoResult = {
  regeocode?: {
    formattedAddress?: string;
    addressComponent?: AmapAddressComponent;
  };
};

type AmapPoi = {
  id?: string;
  name?: string;
  address?: string;
  pname?: string;
  cityname?: string;
  adname?: string;
  location?: AmapLocation;
};

type AmapMap = {
  on: (eventName: "click", handler: (event: AmapClickEvent) => void) => void;
  setCenter: (center: [number, number]) => void;
  setZoom: (zoom: number) => void;
  destroy: () => void;
};

type AmapMarker = {
  setMap: (map: AmapMap | null) => void;
  setPosition: (position: [number, number]) => void;
};

type AmapWindow = Window & {
  AMap?: {
    Map: new (container: HTMLElement, options: Record<string, unknown>) => AmapMap;
    Marker: new (options: Record<string, unknown>) => AmapMarker;
    PlaceSearch: new (options: Record<string, unknown>) => {
      search: (keyword: string, callback: (status: string, result: { poiList?: { pois?: AmapPoi[] } }) => void) => void;
    };
    Geocoder: new (options: Record<string, unknown>) => {
      getAddress: (
        location: [number, number],
        callback: (status: string, result: AmapRegeoResult) => void
      ) => void;
    };
  };
  _AMapSecurityConfig?: {
    securityJsCode?: string;
  };
};

const authStore = useAuthStore();
const errorMessage = ref("");
const successMessage = ref("");
const avatarInput = ref<HTMLInputElement | null>(null);
const mapPreviewRef = ref<HTMLElement | null>(null);
const mapPickerRef = ref<HTMLElement | null>(null);
const avatarUploading = ref(false);
const localAvatarPreview = ref("");

const form = reactive({
  nickname: "",
  realName: "",
  phone: "",
  email: "",
  avatarUrl: "",
  gender: 0,
  education: "",
  workYears: 0
});

const addressForm = reactive({
  id: "",
  addressName: "家",
  province: "",
  city: "",
  district: "",
  detailAddress: "",
  longitude: "",
  latitude: ""
});

const amapKeyword = ref("");
const amapPois = ref<AmapPoi[]>([]);
const amapSearching = ref(false);
const mapPickerOpen = ref(false);
const addressLoading = ref(false);
const addressSaving = ref(false);
const addressError = ref("");
const addressSuccess = ref("");
const selectedMapText = ref("还没有选择地址");
let amapLoader: Promise<void> | null = null;
let placeSearch: InstanceType<NonNullable<AmapWindow["AMap"]>["PlaceSearch"]> | null = null;
let geocoder: InstanceType<NonNullable<AmapWindow["AMap"]>["Geocoder"]> | null = null;
let previewMap: AmapMap | null = null;
let pickerMap: AmapMap | null = null;
let previewMarker: AmapMarker | null = null;
let pickerMarker: AmapMarker | null = null;

const avatarPreviewUrl = computed(() => localAvatarPreview.value || normalizeAvatarUrl(form.avatarUrl));
const avatarText = computed(() => avatarInitial(authStore.displayName));
const amapConfigTip = computed(() => {
  if (!getAmapKey()) {
    return "未配置高德 Key 时，可以手动填写地址；如需高德搜索，请在前端环境变量中配置 VITE_AMAP_KEY。";
  }
  return "可以输入关键词调用高德搜索，选择结果后会自动回填地址和经纬度。";
});

function fillForm() {
  const user = authStore.user;
  if (!user) return;
  form.nickname = user.nickname || "";
  form.realName = user.realName || "";
  form.phone = user.phone || "";
  form.email = user.email || "";
  form.avatarUrl = user.avatarUrl || "";
  form.gender = user.gender || 0;
  form.education = user.education || "";
  form.workYears = Number(user.workYears || 0);
  localAvatarPreview.value = "";
}

onMounted(async () => {
  await authStore.loadMe();
  fillForm();
  if (authStore.isLogin) {
    await loadAddress();
    await nextTick();
    initPreviewMap().catch(error => {
      addressError.value = error instanceof Error ? error.message : "地图预览加载失败";
    });
  }
});

watch(() => authStore.user, fillForm);

watch(
  () => [addressForm.longitude, addressForm.latitude],
  () => {
    syncMapMarkers();
  }
);

onBeforeUnmount(() => {
  // 1. 页面离开时销毁地图实例，避免地图事件和 DOM 节点残留。
  previewMap?.destroy();
  pickerMap?.destroy();
});

function openAvatarPicker() {
  if (!avatarUploading.value) {
    avatarInput.value?.click();
  }
}

async function changeAvatar(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (!file) {
    return;
  }
  if (!file.type.startsWith("image/")) {
    errorMessage.value = "头像只支持图片文件";
    input.value = "";
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    errorMessage.value = "头像文件不能超过2MB";
    input.value = "";
    return;
  }

  errorMessage.value = "";
  successMessage.value = "";
  avatarUploading.value = true;
  localAvatarPreview.value = URL.createObjectURL(file);

  try {
    const result = await uploadAvatar(file);
    form.avatarUrl = result.url;
    localAvatarPreview.value = normalizeAvatarUrl(result.url);
    authStore.setUser(await updateProfile(form));
    successMessage.value = "头像已更新";
  } catch (error) {
    localAvatarPreview.value = "";
    errorMessage.value = error instanceof Error ? error.message : "头像上传失败";
  } finally {
    avatarUploading.value = false;
    input.value = "";
  }
}

async function submit() {
  errorMessage.value = "";
  successMessage.value = "";
  try {
    authStore.setUser(await updateProfile(form));
    successMessage.value = "资料已更新";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存失败";
  }
}

async function loadAddress() {
  addressLoading.value = true;
  addressError.value = "";
  try {
    const address = await getDefaultAddress();
    fillAddressForm(address);
  } catch (error) {
    addressError.value = error instanceof Error ? error.message : "地址加载失败";
  } finally {
    addressLoading.value = false;
  }
}

async function submitAddress() {
  addressError.value = "";
  addressSuccess.value = "";

  if (!hasAddressContent()) {
    addressError.value = "请填写家庭地址";
    return;
  }

  addressSaving.value = true;
  try {
    await ensureAddressRegionReady();
    if (!addressForm.province || !addressForm.city || !addressForm.district) {
      throw new Error("省份、城市、区县没有补齐，请在地图中重新选择地址或手动填写");
    }
    const payload = buildAddressPayload();
    console.log("[Job-Agent] 保存家庭地址参数", payload);

    const savedAddress = await saveDefaultAddress(payload);
    fillAddressForm(savedAddress);
    addressSuccess.value = "家庭地址已保存";
  } catch (error) {
    addressError.value = error instanceof Error ? error.message : "地址保存失败";
  } finally {
    addressSaving.value = false;
  }
}

function buildAddressPayload() {
  // 1. 保存前统一 trim，避免看起来有内容、实际全是空格的字段传到后端。
  return {
    id: trimToUndefined(addressForm.id),
    addressName: trimToUndefined(addressForm.addressName) || "家",
    province: trimToUndefined(addressForm.province),
    city: trimToUndefined(addressForm.city),
    district: trimToUndefined(addressForm.district),
    detailAddress: trimToUndefined(addressForm.detailAddress),
    longitude: trimToUndefined(addressForm.longitude),
    latitude: trimToUndefined(addressForm.latitude)
  };
}

async function searchAmapAddress() {
  const keyword = amapKeyword.value.trim();
  if (!keyword) {
    addressError.value = "请输入要搜索的地址关键词";
    return;
  }
  if (!getAmapKey()) {
    addressError.value = "请先配置 VITE_AMAP_KEY，再使用高德地址搜索";
    return;
  }

  amapSearching.value = true;
  addressError.value = "";
  addressSuccess.value = "";
  try {
    await ensureAmapLoaded();
    const pois = await searchPois(keyword);
    amapPois.value = pois;
    if (!pois.length) {
      addressError.value = "没有搜索到匹配地址，可以尝试更换关键词或手动填写";
    }
  } catch (error) {
    addressError.value = error instanceof Error ? error.message : "高德地址搜索失败";
  } finally {
    amapSearching.value = false;
  }
}

async function selectAmapPoi(poi: AmapPoi) {
  const longitude = getLongitude(poi.location);
  const latitude = getLatitude(poi.location);

  addressForm.addressName = poi.name || addressForm.addressName || "家";
  addressForm.province = normalizeAmapText(poi.pname) || addressForm.province;
  addressForm.city = normalizeAmapText(poi.cityname) || addressForm.city;
  addressForm.district = normalizeAmapText(poi.adname) || addressForm.district;
  addressForm.detailAddress = [poi.name, poi.address].filter(Boolean).join(" ");
  addressForm.longitude = longitude == null ? "" : String(longitude);
  addressForm.latitude = latitude == null ? "" : String(latitude);
  amapKeyword.value = formatPoiAddress(poi);
  amapPois.value = [];
  movePickerMapToFormAddress();
  syncMapMarkers();

  if (longitude != null && latitude != null) {
    await fillAddressByLngLat(longitude, latitude, {
      addressName: poi.name,
      detailAddress: [poi.name, poi.address].filter(Boolean).join(" ")
    });
  }

  addressError.value = "";
  addressSuccess.value = "已选择高德地址，确认无误后点击保存家庭地址";
  selectedMapText.value = buildAddressSummary();
}

function fillAddressForm(address: UserAddressInfo | null) {
  addressForm.id = address?.id || "";
  addressForm.addressName = address?.addressName || "家";
  addressForm.province = address?.province || "";
  addressForm.city = address?.city || "";
  addressForm.district = address?.district || "";
  addressForm.detailAddress = address?.detailAddress || "";
  addressForm.longitude = address?.longitude == null ? "" : String(address.longitude);
  addressForm.latitude = address?.latitude == null ? "" : String(address.latitude);
  selectedMapText.value = buildAddressSummary();
  syncMapMarkers();
}

function hasAddressContent() {
  return Boolean(
    addressForm.province ||
      addressForm.city ||
      addressForm.district ||
      addressForm.detailAddress ||
      addressForm.longitude ||
      addressForm.latitude
  );
}

async function openMapPicker() {
  if (!getAmapKey()) {
    addressError.value = "请先配置 VITE_AMAP_KEY，再打开地图选址";
    return;
  }

  mapPickerOpen.value = true;
  addressError.value = "";
  addressSuccess.value = "";

  try {
    await nextTick();
    await ensureAmapLoaded();
    initPickerMap();
    movePickerMapToFormAddress();
  } catch (error) {
    addressError.value = error instanceof Error ? error.message : "地图打开失败";
  }
}

function closeMapPicker() {
  destroyPickerMap();
  mapPickerOpen.value = false;
  amapPois.value = [];
}

function confirmMapSelection() {
  if (!hasAddressContent()) {
    addressError.value = "请先在地图上搜索或点击一个地址";
    return;
  }
  destroyPickerMap();
  mapPickerOpen.value = false;
  amapPois.value = [];
  addressSuccess.value = "已回填地图地址，确认无误后点击保存家庭地址";
}

async function initPreviewMap() {
  if (!getAmapKey() || !mapPreviewRef.value) {
    return;
  }
  await ensureAmapLoaded();

  if (!previewMap && mapPreviewRef.value) {
    // 1. 小地图只做地址预览，主要用于让用户确认当前选择的位置。
    previewMap = new (window as AmapWindow).AMap!.Map(mapPreviewRef.value, {
      zoom: 13,
      center: getCurrentCenter(),
      viewMode: "2D"
    });
    previewMarker = new (window as AmapWindow).AMap!.Marker({
      position: getCurrentCenter(),
      map: previewMap
    });
  }
  syncMapMarkers();
}

function initPickerMap() {
  if (!mapPickerRef.value || pickerMap) {
    return;
  }

  // 1. 弹窗地图支持点击选点，点击后用 Geocoder 反查省市区和详细地址。
  pickerMap = new (window as AmapWindow).AMap!.Map(mapPickerRef.value, {
    zoom: 13,
    center: getCurrentCenter(),
    viewMode: "2D"
  });
  pickerMarker = new (window as AmapWindow).AMap!.Marker({
    position: getCurrentCenter(),
    map: pickerMap
  });
  pickerMap.on("click", event => {
    const longitude = getLongitude(event.lnglat);
    const latitude = getLatitude(event.lnglat);
    if (longitude == null || latitude == null) {
      return;
    }
    fillAddressByLngLat(longitude, latitude).catch(error => {
      addressError.value = error instanceof Error ? error.message : "地图地址解析失败";
    });
  });
}

function destroyPickerMap() {
  // 1. 弹窗关闭时销毁大地图，下一次打开再绑定新的 DOM 容器。
  pickerMarker?.setMap(null);
  pickerMarker = null;
  pickerMap?.destroy();
  pickerMap = null;
}

function ensureAmapLoaded() {
  const amapWindow = window as AmapWindow;
  if (amapWindow.AMap) {
    createAmapTools();
    return Promise.resolve();
  }

  if (!amapLoader) {
    amapLoader = new Promise((resolve, reject) => {
      const key = getAmapKey();
      if (!key) {
        reject(new Error("请先配置 VITE_AMAP_KEY"));
        return;
      }

      const securityJsCode = getAmapSecurityJsCode();
      if (securityJsCode) {
        amapWindow._AMapSecurityConfig = { securityJsCode };
      }

      const script = document.createElement("script");
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}&plugin=AMap.PlaceSearch,AMap.Geocoder`;
      script.async = true;
      script.onload = () => {
        createAmapTools();
        resolve();
      };
      script.onerror = () => reject(new Error("高德地图脚本加载失败"));
      document.head.appendChild(script);
    });
  }
  return amapLoader;
}

function createAmapTools() {
  const amapWindow = window as AmapWindow;
  if (!placeSearch && amapWindow.AMap) {
    // 1. city 使用全国，用户可以搜索任意城市的家庭地址。
    placeSearch = new amapWindow.AMap.PlaceSearch({
      city: "全国",
      pageSize: 8,
      pageIndex: 1
    });
  }
  if (!geocoder && amapWindow.AMap) {
    geocoder = new amapWindow.AMap.Geocoder({
      city: "全国"
    });
  }
}

function searchPois(keyword: string) {
  return new Promise<AmapPoi[]>((resolve, reject) => {
    if (!placeSearch) {
      reject(new Error("高德地址搜索未初始化"));
      return;
    }

    placeSearch.search(keyword, (status, result) => {
      if (status !== "complete") {
        reject(new Error("高德地址搜索失败，请稍后重试"));
        return;
      }
      resolve(result.poiList?.pois || []);
    });
  });
}

type AddressFillOptions = {
  addressName?: string;
  detailAddress?: string;
};

async function fillAddressByLngLat(longitude: number, latitude: number, options: AddressFillOptions = {}) {
  addressForm.longitude = longitude.toFixed(6);
  addressForm.latitude = latitude.toFixed(6);
  movePickerMapToFormAddress();
  syncMapMarkers();

  if (!geocoder) {
    selectedMapText.value = buildAddressSummary();
    return;
  }

  try {
    const result = await reverseGeocode(longitude, latitude);
    applyRegeoResult(result, options);
    selectedMapText.value = buildAddressSummary();
    addressSuccess.value = "已根据地图位置回填地址，确认无误后点击保存家庭地址";
  } catch {
    selectedMapText.value = buildAddressSummary();
    addressError.value = "已选中坐标，但地址解析失败，可以手动补充详细地址";
  }
}

function reverseGeocode(longitude: number, latitude: number) {
  return new Promise<AmapRegeoResult>((resolve, reject) => {
    if (!geocoder) {
      reject(new Error("高德逆地理编码未初始化"));
      return;
    }

    geocoder.getAddress([longitude, latitude], (status, result) => {
      if (status !== "complete" || !result.regeocode) {
        reject(new Error("高德逆地理编码失败"));
        return;
      }
      resolve(result);
    });
  });
}

function applyRegeoResult(result: AmapRegeoResult, options: AddressFillOptions = {}) {
  const component = result.regeocode?.addressComponent || {};
  const province = normalizeAmapText(component.province);
  const city = normalizeAmapText(component.city) || province;
  const district = normalizeAmapText(component.district);

  // 1. 省市区以逆地理编码结果为准，避免 POI 搜索结果缺字段导致保存为空。
  addressForm.addressName = options.addressName || addressForm.addressName || "家";
  addressForm.province = province;
  addressForm.city = city;
  addressForm.district = district;
  addressForm.detailAddress = options.detailAddress || buildDetailAddress(result.regeocode?.formattedAddress, component);
}

async function ensureAddressRegionReady() {
  // 1. 如果省市区已经都有值，就不再调用高德，避免多余请求。
  if (addressForm.province && addressForm.city && addressForm.district) {
    return;
  }

  const center = getCurrentCenterOrNull();
  if (!center || !getAmapKey()) {
    return;
  }

  await ensureAmapLoaded();
  const result = await reverseGeocode(center[0], center[1]);
  applyRegeoResult(result, {
    addressName: addressForm.addressName,
    detailAddress: addressForm.detailAddress
  });
}

function buildDetailAddress(formattedAddress?: string, component?: AmapAddressComponent) {
  if (formattedAddress) {
    return formattedAddress;
  }
  return [
    component?.township,
    component?.street,
    component?.streetNumber
  ].filter(Boolean).join("");
}

function syncMapMarkers() {
  const center = getCurrentCenterOrNull();
  if (!center) {
    return;
  }

  if (previewMap) {
    previewMap.setCenter(center);
    previewMap.setZoom(15);
  }
  if (previewMarker) {
    previewMarker.setPosition(center);
  }
  if (pickerMap) {
    pickerMap.setCenter(center);
    pickerMap.setZoom(15);
  }
  if (pickerMarker) {
    pickerMarker.setPosition(center);
  }
}

function movePickerMapToFormAddress() {
  const center = getCurrentCenterOrNull();
  if (!center || !pickerMap) {
    return;
  }
  pickerMap.setCenter(center);
  pickerMap.setZoom(15);
}

function getCurrentCenter() {
  return getCurrentCenterOrNull() || [123.4315, 41.8057];
}

function getCurrentCenterOrNull(): [number, number] | null {
  const longitude = Number(addressForm.longitude);
  const latitude = Number(addressForm.latitude);
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    return null;
  }
  return [longitude, latitude];
}

function buildAddressSummary() {
  const text = [
    addressForm.province,
    addressForm.city,
    addressForm.district,
    addressForm.detailAddress
  ].filter(Boolean).join(" ");
  return text || "还没有选择地址";
}

function formatPoiAddress(poi: AmapPoi) {
  return [
    normalizeAmapText(poi.pname) || addressForm.province,
    normalizeAmapText(poi.cityname) || addressForm.city,
    normalizeAmapText(poi.adname) || addressForm.district,
    poi.address
  ].filter(Boolean).join(" ") || poi.name || "";
}

function normalizeAmapText(value?: string | string[]) {
  if (Array.isArray(value)) {
    return "";
  }
  if (!value || value === "[]") {
    return "";
  }
  return value;
}

function trimToUndefined(value?: string) {
  if (!value || !value.trim()) {
    return undefined;
  }
  return value.trim();
}

function getLongitude(location?: AmapLocation) {
  if (!location) {
    return null;
  }
  if (typeof location.getLng === "function") {
    return location.getLng();
  }
  return location.lng ?? null;
}

function getLatitude(location?: AmapLocation) {
  if (!location) {
    return null;
  }
  if (typeof location.getLat === "function") {
    return location.getLat();
  }
  return location.lat ?? null;
}

function getAmapKey() {
  return (import.meta.env.VITE_AMAP_KEY || "").trim();
}

function getAmapSecurityJsCode() {
  return (import.meta.env.VITE_AMAP_SECURITY_JS_CODE || "").trim();
}
</script>
